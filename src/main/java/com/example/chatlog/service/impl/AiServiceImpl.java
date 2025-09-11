package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AiServiceImpl implements AiService {
    // Lưu trữ thông tin mapping của Elasticsearch index để tránh gọi lại nhiều lần
    private static String fieldLog;

    // Client để giao tiếp với AI model (Spring AI)
    private final ChatClient chatClient;

    @Autowired
    private LogApiService logApiService;

    /**
     * Lấy thông tin mapping (cấu trúc field) của Elasticsearch index
     * Chỉ gọi API một lần và cache kết quả để tối ưu hiệu suất
     * @return String chứa thông tin mapping dạng JSON
     */
    public String getFieldLog()
    {
        if (fieldLog == null)
        {
            fieldLog = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
        }
        return fieldLog;
    }

    /**
     * Tạo chuỗi ngày tháng cho system message với tính toán các ngày trước đó
     * @param now Thời điểm hiện tại
     * @return Chuỗi chứa thông tin về các ngày để sử dụng trong prompt
     */
    private String generateDateContext(LocalDateTime now) {
        String currentDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String yesterday = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String twoDaysAgo = now.minusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String threeDaysAgo = now.minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fourDaysAgo = now.minusDays(4).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fiveDaysAgo = now.minusDays(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String oneWeekAgo = now.minusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        return String.format("""
                IMPORTANT DATE HANDLING (Use English for better AI understanding):
                - Current date is: %s
                - Yesterday was: %s
                - 2 days ago was: %s
                - 3 days ago was: %s
                - 4 days ago was: %s
                - 5 days ago was: %s
                - 1 week ago was: %s
                
                Date Range Mapping Rules:
                - "today", "hôm nay" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "yesterday", "hôm qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "last 2 days", "2 ngày qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "last 3 days", "3 ngày qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "last 4 days", "4 ngày qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "last 5 days", "5 ngày qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "last week", "1 tuần qua" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "recent", "gần đây", "mới nhất" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                
                Always use Vietnam timezone (+07:00) for all timestamps.
                """,
                currentDate, yesterday, twoDaysAgo, threeDaysAgo, fourDaysAgo, fiveDaysAgo, oneWeekAgo,
                currentDate, currentDate,
                yesterday, yesterday,
                twoDaysAgo, currentDate,
                threeDaysAgo, currentDate,
                fourDaysAgo, currentDate,
                fiveDaysAgo, currentDate,
                oneWeekAgo, currentDate,
                twoDaysAgo, currentDate);
    }

    /**
     * Constructor khởi tạo AiServiceImpl với ChatClient và memory
     * @param builder ChatClient.Builder để xây dựng client AI
     * @param jdbcChatMemoryRepository Repository lưu trữ lịch sử chat
     */
    public AiServiceImpl(ChatClient.Builder builder,  JdbcChatMemoryRepository jdbcChatMemoryRepository) {

        // Khởi tạo memory để lưu trữ lịch sử chat của người dùng (tối đa 50 tin nhắn)
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(jdbcChatMemoryRepository)
            .maxMessages(50)
            .build();

        // Xây dựng ChatClient với memory advisor để duy trì ngữ cảnh cuộc trò chuyện
        this.chatClient = builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();

    }

    /**
     * Hàm chính xử lý yêu cầu của người dùng
     * Quy trình 3 bước:
     * 1. Phân tích câu hỏi và tạo Elasticsearch query (bắt buộc cho tất cả request)
     * 2. Thực hiện tìm kiếm Elasticsearch và lấy dữ liệu log
     * 3. Tóm tắt và trả lời bằng ngôn ngữ tự nhiên
     * 
     * @param sessionId ID phiên chat để duy trì ngữ cảnh
     * @param chatRequest Yêu cầu từ người dùng
     * @return Câu trả lời đã được xử lý
     */
    @Override
    public String handleRequest(Long sessionId, ChatRequest chatRequest) {

        String content;
        RequestBody requestBody;
        
        // Bước 1: Tạo system message hướng dẫn AI phân tích yêu cầu
        // Lấy ngày hiện tại để AI có thể xử lý các yêu cầu về thời gian chính xác
        LocalDateTime now = LocalDateTime.now();
        // System.out.println(now);
        String dateContext = generateDateContext(now);
        // System.out.println(dateContext);
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You are an Elasticsearch Query Generator. Your ONLY job is to convert user questions into Elasticsearch queries.
                
                %s
                
                CRITICAL RULES - FOLLOW EXACTLY:
                1. NEVER give direct answers or summaries
                2. NEVER say things like "Trong 5 ngày qua, có 50 kết nối..."
                3. ALWAYS generate an Elasticsearch query JSON
                4. ALWAYS return the exact JSON format below
                
                MANDATORY OUTPUT FORMAT (copy this structure exactly):
                {
                  "query": 1,
                  "body": "{\\"query\\":{\\"bool\\":{\\"must\\":[...]}},\\"size\\":100,\\"_source\\":[...]}"
                }
                
                EXAMPLE CORRECT RESPONSES:
                Question: "Show connections from IP 10.0.30.199 in last 3 days"
                Response: {"query":1,"body":"{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"source.ip\\":\\"10.0.30.199\\"}},{\\"range\\":{\\"@timestamp\\":{\\"gte\\":\\"2025-09-07T00:00:00+07:00\\",\\"lte\\":\\"2025-09-10T23:59:59+07:00\\"}}}]}},\\"size\\":100,\\"_source\\":[\\"@timestamp\\",\\"source.ip\\",\\"destination.ip\\",\\"event.action\\"]}"}
                
                Question: "Count failed logins today"  
                Response: {"query":1,"body":"{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"event.action\\":\\"login\\"}},{\\"term\\":{\\"event.outcome\\":\\"failure\\"}},{\\"range\\":{\\"@timestamp\\":{\\"gte\\":\\"2025-09-10T00:00:00+07:00\\",\\"lte\\":\\"2025-09-10T23:59:59+07:00\\"}}}]}},\\"aggs\\":{\\"login_count\\":{\\"value_count\\":{\\"field\\":\\"event.action\\"}}},\\"size\\":0}"}
                
                WRONG EXAMPLES (NEVER DO THIS):
                ❌ "Trong 5 ngày qua, có 50 kết nối được mở bởi IP 10.0.30.199"
                ❌ "Based on the logs, there were 30 connections..."
                ❌ Any text that is not the JSON format above
                
                Available Elasticsearch fields:
                %s
                
                Generate ONLY the JSON response. No explanations, no summaries, just the JSON.
                """, 
                dateContext,
                getFieldLog()));

        // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định
        ChatOptions chatOptions = ChatOptions.builder()
            .temperature(0D)
            .build();

        UserMessage userMessage = new UserMessage(chatRequest.message());
        System.out.println(userMessage);
        System.out.println("----------------------------------------------------------");
        System.out.println(systemMessage);
        Prompt prompt = new Prompt(systemMessage, userMessage);


        // Gọi AI để phân tích và tạo request body
        requestBody =  chatClient
            .prompt(prompt)
            .options(chatOptions)
            .call()
            .entity(new ParameterizedTypeReference<>() {
            });

        System.out.println("THong tin quey: "+requestBody.getQuery());
        System.out.println("[AiServiceImpl] Generated query body: " + requestBody.getBody());
        System.out.println("[AiServiceImpl] Using current date context: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        // Validation: Kiểm tra xem body có phải là JSON query hay không
        if (requestBody.getBody() != null) {
            String body = requestBody.getBody().trim();
            
            // Kiểm tra format JSON hợp lệ
            if (!body.startsWith("{") || !body.contains("\"query\"")) {
                System.out.println("[AiServiceImpl] ERROR: Invalid JSON format!");
                System.out.println("[AiServiceImpl] Expected: JSON starting with { and containing 'query'");
                System.out.println("[AiServiceImpl] Received: " + body);
                return "❌ AI model trả về format không đúng. Cần JSON query, nhận được: " + body;
            }
            
            // Kiểm tra không phải câu trả lời trực tiếp
            if (body.toLowerCase().contains("trong") || body.toLowerCase().contains("ngày qua") || 
                body.toLowerCase().contains("kết nối") || body.toLowerCase().contains("connections")) {
                System.out.println("[AiServiceImpl] ERROR: AI returned direct answer instead of query!");
                System.out.println("[AiServiceImpl] Body content: " + body);
                return "❌ AI model đã trả lời trực tiếp thay vì tạo Elasticsearch query. Đang thử lại...";
            }
        }

        // Bước 2: Luôn thực hiện tìm kiếm Elasticsearch (vì đã bắt buộc query = 1)
        // Cần tìm kiếm: gọi Elasticsearch và lấy dữ liệu log
        content =  logApiService.search("logs-fortinet_fortigate.log-default*",
            requestBody.getBody());

        // Bước 3: Tóm tắt kết quả và trả lời người dùng
        return getAiResponse(sessionId,chatRequest,content, requestBody.getBody());
    }


    /**
     * Tóm tắt và diễn giải dữ liệu log thành ngôn ngữ tự nhiên
     * Sử dụng AI để phân tích kết quả từ Elasticsearch và tạo câu trả lời dễ hiểu
     * 
     * @param sessionId ID phiên chat để duy trì ngữ cảnh
     * @param chatRequest Yêu cầu gốc từ người dùng
     * @param content Dữ liệu log từ Elasticsearch hoặc câu trả lời trực tiếp
     * @param query Query đã được sử dụng để tìm kiếm
     * @return Câu trả lời bằng ngôn ngữ tự nhiên
     */
    public String getAiResponse(Long sessionId,ChatRequest chatRequest, String content,String query) {
        String conversationId = sessionId.toString();

        // Tạo system message hướng dẫn AI cách phản hồi
        SystemMessage systemMessage = new SystemMessage("""
                You are HPT.AI
                You should respond in a formal voice.
                If the query is executed but no results are found, return the Elasticsearch query body itself, summary of the result query.
                logData :
                """ + content+" query: " + query);

        UserMessage userMessage = new UserMessage(chatRequest.message());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        // Gọi AI với ngữ cảnh cuộc trò chuyện để tạo phản hồi
        return chatClient
            .prompt(prompt)
            .advisors(advisorSpec -> advisorSpec.param(
                ChatMemory.CONVERSATION_ID, conversationId
            ))
            .call()
            .content();
    }

    /**
     * Xử lý yêu cầu với file đính kèm (hình ảnh, tài liệu, v.v.)
     * Cho phép người dùng gửi file cùng với tin nhắn để AI phân tích
     * 
     * @param sessionId ID phiên chat để duy trì ngữ cảnh
     * @param file File được upload bởi người dùng
     * @param request Yêu cầu kèm theo từ người dùng
     * @param content Nội dung bổ sung (nếu có)
     * @return Phản hồi của AI sau khi phân tích file và tin nhắn
     */
    public String getAiResponse(Long sessionId, MultipartFile file, ChatRequest request, String content) {
        String conversationId = sessionId.toString();

        // Tạo đối tượng Media từ file upload
        Media media = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
            .data(file.getResource())
            .build();

        // Gọi AI với cả media và text, duy trì ngữ cảnh cuộc trò chuyện
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
}
