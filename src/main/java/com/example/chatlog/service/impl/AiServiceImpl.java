package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiServiceImpl implements AiService {
    // Lưu tạm metadata field của index (mapping) để dùng trong prompt
    private static String fieldLog;

    private final ChatClient chatClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Bật/tắt hiển thị debug thông tin ES (query, count) trên giao diện
    private static final boolean UI_DEBUG = true;

    @Autowired
    private LogApiService logApiService;

    // Lấy metadata field (mapping) của chỉ mục log. Cache lại để tránh gọi lặp.
    public String getFieldLog()
    {
        if (fieldLog == null)
        {
            System.out.println("[AiServiceImpl] Fetch field mapping from Elasticsearch...");
            fieldLog = logApiService.getFieldLog("logs-fortinet_fortigate.log-default*");
            System.out.println("[AiServiceImpl] Field mapping loaded, length=" + (fieldLog != null ? fieldLog.length() : 0));
        }
        return fieldLog;
    }

    public AiServiceImpl(ChatClient.Builder builder,  JdbcChatMemoryRepository jdbcChatMemoryRepository) {


        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(50)
                .build();


        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

    }

    @Override
    public String handleRequest(Long sessionId, ChatRequest chatRequest) {
        // Pha 1: Dùng LLM để quyết định có cần truy vấn ES hay không, và sinh JSON nếu cần
        RequestBody requestBody;
        SystemMessage systemMessage = new SystemMessage("""
                Read the user message and decide whether Elasticsearch data is required.

                - If NO Elasticsearch data is required: set query=0 and set body to the final natural-language answer for the user. Do not include any Elasticsearch JSON.
                - If Elasticsearch data IS required: set query=1 and set body to a VALID Elasticsearch search request JSON for POST /{index}/_search.

                When building Elasticsearch JSON:
                - If the message contains date values, include range with gte and lte in the format 2025-09-06T23:59:59+07:00.
                - Only include fields relevant to the question using _source filtering.
                - Prefer adding a small size (e.g. 20) to limit result count.
                - Ensure the JSON is syntactically valid and does not contain trailing commas.

                metadata_field:
                """ + getFieldLog());


        ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0D)
                .build();


        UserMessage userMessage = new UserMessage(chatRequest.message());

        Prompt prompt = new Prompt(systemMessage, userMessage);

        requestBody =  chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });

        // Giữ lại log cũ để bạn dễ đối chiếu
        System.out.println("THong tin quey: " + requestBody.getQuery());
        System.out.println("[AiServiceImpl] Phase-1 decision, query=" + requestBody.getQuery());
        System.out.println("[AiServiceImpl] Phase-1 body (truncated 1k): " + (requestBody.getBody() != null && requestBody.getBody().length() > 1000 ? requestBody.getBody().substring(0,1000) + "..." : requestBody.getBody()));
        if (requestBody.getQuery() == 1) {
            // In đẹp JSON truy vấn ES để kiểm tra (nếu có)
            System.out.println("[AiServiceImpl] Phase-1 ES query JSON (pretty):\n" + prettyJsonSafe(requestBody.getBody()));
        }

        if (requestBody.getQuery() == 0) {
            // Không cần dữ liệu log: body là câu trả lời cuối cùng
            System.out.println("[AiServiceImpl] No ES needed. Returning final answer from Phase-1.");
            return requestBody.getBody();
        }

        // Pha 2: Gọi ES để lấy log, sau đó rút gọn chỉ giữ _source để tóm tắt
        String esResponse = logApiService.search("logs-fortinet_fortigate.log-default*",
                requestBody.getBody());
        System.out.println("[AiServiceImpl] ES raw response (truncated 2k): " + (esResponse != null && esResponse.length() > 2000 ? esResponse.substring(0,2000) + "..." : esResponse));
        int requestedSize = extractRequestedSize(requestBody.getBody(), 20);
        int cap = Math.min(requestedSize, 50);
        String reducedSources = extractSources(esResponse, cap);
        int reducedCount = countArrayItemsSafe(reducedSources);
        System.out.println("[AiServiceImpl] Reduced _source array count=" + reducedCount + " (cap=" + cap + ")");

        String summarized = getAiResponse(sessionId, chatRequest, reducedSources, requestBody.getBody());
        if (UI_DEBUG) {
            String debugBlock = "\n\n---\n" +
                    "Dưới đây là truy vấn Elasticsearch (debug):\n\n" +
                    "```json\n" + prettyJsonSafe(requestBody.getBody()) + "\n```\n" +
                    "Số bản ghi sử dụng để tóm tắt: " + reducedCount + " (cap=" + cap + ")\n";
            return summarized + debugBlock;
        }
        return summarized;
    }


    // Gọi LLM để tóm tắt/diễn giải dữ liệu log sang ngôn ngữ tự nhiên
    public String getAiResponse(Long sessionId,ChatRequest chatRequest, String content,String query) {
        String conversationId = sessionId.toString();

        SystemMessage systemMessage = new SystemMessage("""
                You are HPT.AI
                You should respond in a formal voice.
                If the query is executed but no results are found, return the Elasticsearch query body itself.
                logData :
                """ + content+" query: " + query);

        UserMessage userMessage = new UserMessage(chatRequest.message());

        Prompt prompt = new Prompt(systemMessage, userMessage);

        return chatClient
                .prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID, conversationId
                ))
                .call()
                .content();
    }

    // Biến file media + text thành prompt để gọi LLM (không liên quan ES)
    public String getAiResponse(Long sessionId, MultipartFile file, ChatRequest request, String content) {
        String conversationId = sessionId.toString();

        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                .data(file.getResource())
                .build();

        return chatClient.prompt()
                .system("")
                .user(promptUserSpec ->promptUserSpec.media(media)
                        .text(request.message()))
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID, conversationId
                ))
                .call()
                .content();
    }

    // Rút gọn dữ liệu ES: chỉ lấy _source của tối đa maxHits bản ghi
    private String extractSources(String esResponse, int maxHits) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(esResponse);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray()) {
                return esResponse;
            }
            ArrayNode limitedSources = OBJECT_MAPPER.createArrayNode();
            int count = 0;
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                if (!source.isMissingNode()) {
                    limitedSources.add(source);
                    count++;
                    if (count >= maxHits) {
                        break;
                    }
                }
            }
            return OBJECT_MAPPER.writeValueAsString(limitedSources);
        } catch (Exception e) {
            System.out.println("[AiServiceImpl] extractSources error: " + e.getMessage());
            return esResponse;
        }
    }

    // Lấy tham số size từ JSON truy vấn ES; nếu không có thì dùng mặc định
    private int extractRequestedSize(String esQueryJson, int defaultSize) {
        try {
            if (esQueryJson == null) return defaultSize;
            JsonNode node = OBJECT_MAPPER.readTree(esQueryJson);
            JsonNode sizeNode = node.path("size");
            if (sizeNode.isInt()) {
                return sizeNode.asInt();
            }
            return defaultSize;
        } catch (Exception e) {
            return defaultSize;
        }
    }

    // Đếm số phần tử trong chuỗi JSON array an toàn
    private int countArrayItemsSafe(String jsonArrayString) {
        try {
            if (jsonArrayString == null) return 0;
            JsonNode node = OBJECT_MAPPER.readTree(jsonArrayString);
            if (node.isArray()) {
                return node.size();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // Pretty print JSON để dễ đọc; nếu không phải JSON hợp lệ thì trả nguyên bản
    private String prettyJsonSafe(String json) {
        try {
            if (json == null) return null;
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }
}
