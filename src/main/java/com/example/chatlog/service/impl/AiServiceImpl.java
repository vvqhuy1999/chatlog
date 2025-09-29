package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.service.ModelConfigService;
import com.example.chatlog.utils.SchemaHint;
import com.example.chatlog.utils.QueryTemplates;
import com.example.chatlog.utils.PromptTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {
  // Lưu trữ thông tin mapping của Elasticsearch index để tránh gọi lại nhiều lần
  private static String fieldLog;

  // Client để giao tiếp với AI model (Spring AI)
  private final ChatClient chatClient;

  @Autowired
  private LogApiService logApiService;
  
  @Autowired
  private ModelConfigService modelConfigService;
  
  @Autowired
  @Qualifier("openRouterChatClient")
  private RestClient openRouterClient;

  /**
   * Tạo chuỗi thông tin ngày tháng cho system message với các biểu thức thời gian tương đối của Elasticsearch
   * @param now Thời điểm hiện tại (real-time)
   * @return Chuỗi chứa thông tin về cách sử dụng biểu thức thời gian tương đối của Elasticsearch
   */
  private String generateDateContext(LocalDateTime now) {
    return String.format("""
                CURRENT TIME CONTEXT (Vietnam timezone +07:00):
                - Current exact time: %s (+07:00)
                - Current date: %s
                
                PREFERRED TIME QUERY METHOD - Use Elasticsearch relative time expressions:
                - "5 phút qua, 5 phút trước, 5 minutes ago", "last 5 minutes" → {"gte": "now-5m"}
                - "1 giờ qua, 1 giờ trước, 1 hour ago", "last 1 hour" → {"gte": "now-1h"}
                - "24 giờ qua, 24 giờ trước, 24 hours ago", "last 24 hours" → {"gte": "now-24h"}
                - "1 tuần qua, 1 tuần trước, 1 week ago", "7 ngày qua, 7 ngày trước, 7 days ago", "last week" → {"gte": "now-7d"}
                - "1 tháng qua, 1 tháng trước, 1 month ago", "last month" → {"gte": "now-30d"}
                
                SPECIFIC DATE RANGES (when exact dates mentioned):
                - "hôm nay, hôm nay, today" → {"gte": "now/d"}
                - "hôm qua, hôm qua, yesterday" → {"gte": "now-1d/d"}
                - Specific date like "ngày 15-09" → {"gte": "2025-09-15T00:00:00.000+07:00", "lte": "2025-09-15T23:59:59.999+07:00"}
                
                ADVANTAGES of "now-Xh/d/m" format:
                - More efficient than absolute timestamps
                - Automatically handles timezone
                - Elasticsearch native time calculations
                - Always relative to query execution time
                """,
        now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    );
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

  String content = "";
  RequestBody requestBody;

    // In ra thông tin cấu hình OpenAI trước khi gọi
    String openaiUrl = System.getenv("OPENAI_API_URL");
    String openaiApiKey = System.getenv("OPENAI_API_KEY");
    String model = "gpt-4o-mini";

    try {
      String baseUrl = System.getProperty("spring.ai.openai.base-url");
      String apiKey = System.getProperty("spring.ai.openai.api-key");
    } catch (Exception ex) {
      System.out.println("[SpringAI] Không lấy được base-url/api-key ");
    }

    // Bước 1: Tạo system message hướng dẫn AI phân tích yêu cầu
    // Lấy ngày hiện tại để AI có thể xử lý các yêu cầu về thời gian chính xác
    LocalDateTime now = LocalDateTime.now();
    String dateContext = generateDateContext(now);

    // Cách 2: Sử dụng SystemPromptTemplate với các placeholder thông qua PromptConverter
    // Không thể sử dụng Map.of với hơn 10 cặp key-value, chuyển sang sử dụng HashMap
    Map<String, String> params = new HashMap<>();
    params.put("name", "ElasticSearch Expert");
    params.put("role", "chuyên gia");
    params.put("expertise", "Elasticsearch Query DSL và phân tích log bảo mật");
    params.put("style", "chuyên nghiệp và chính xác");
    params.put("constraints", "Chỉ trả về JSON query, không giải thích");
    params.put("dateContext", dateContext);
    params.put("fieldMappings", SchemaHint.getSchemaHint());
    params.put("categoryGuides", SchemaHint.getCategoryGuides());
    params.put("roleNormalizationRules", SchemaHint.getRoleNormalizationRules());
    params.put("exampleQueries", String.join("\n\n", 
        SchemaHint.getNetworkTrafficExamples(),
        SchemaHint.getIPSSecurityExamples(),
        SchemaHint.getAdminRoleExample(),
        SchemaHint.getGeographicExamples(),
        SchemaHint.getFirewallRuleExamples(),
        SchemaHint.getCountingExamples(),
        SchemaHint.getQuickPatterns()
    ));
    params.put("currentTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    params.put("indexSchema", SchemaHint.getSchemaHint());
    params.put("complexityLevel", "Advanced - Hỗ trợ nested aggregations và bucket selectors");
    params.put("maxSize", "1000");
    
    // Sử dụng QueryPromptTemplate: đưa toàn bộ thư viện + ví dụ động (nếu có)
    Map<String, Object> dynamicInputs = new HashMap<>();
    String queryPrompt = com.example.chatlog.utils.QueryPromptTemplate.createQueryGenerationPromptWithAllTemplates(
        chatRequest.message(),
        dateContext,
        SchemaHint.getSchemaHint(),
        SchemaHint.getRoleNormalizationRules(),
        dynamicInputs
    );
    SystemMessage systemMessage = new SystemMessage(queryPrompt);


    List<String> schemaHints = SchemaHint.allSchemas();
    String schemaContext = String.join("\n\n", schemaHints);
    UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);
    // Provide a single sample log to help AI infer fields and structure
    UserMessage sampleLogMsg = new UserMessage("SAMPLE LOG (for inference):\n" + SchemaHint.examplelog());

    UserMessage userMessage = new UserMessage(chatRequest.message());

    System.out.println("----------------------------------------------------------");
    Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, sampleLogMsg, userMessage));

    // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định và tuân thủ strict
    ChatOptions chatOptions = ChatOptions.builder()
        .temperature(0.0D)
        .build();

    // Gọi AI để phân tích và tạo request body
    try {
      // SỬA: Sử dụng conversationId riêng cho query generation để tránh memory contamination
      String queryConversationId = sessionId + "_query_generation";
      
      System.out.println("[AiServiceImpl] 🤖 Đang gọi AI để tạo Elasticsearch query...");
      requestBody =  chatClient
          .prompt(prompt)
          .options(chatOptions)
          .advisors(advisorSpec -> advisorSpec.param(
              ChatMemory.CONVERSATION_ID, queryConversationId
          ))
          .call()
          .entity(new ParameterizedTypeReference<>() {
          });

      // Log response trả về từ OpenAI
      System.out.println("[SpringAI] --- HTTP RESPONSE ---");
      // Không lấy được status code và header trực tiếp, chỉ log body
      System.out.println("Body:");
      System.out.println(requestBody);
//      System.out.println("---------------------------");
    } catch (Exception e) {
      // Kiểm tra loại lỗi và xử lý phù hợp
      if (e.getMessage() != null) {
        if (e.getMessage().contains("503") || e.getMessage().contains("upstream connect error")) {
          System.out.println("[AiServiceImpl] ⚠️ AI Service tạm thời không khả dụng (HTTP 503). Đang thử lại...");
          return "⚠️ **AI Service tạm thời không khả dụng**\n\n" +
                 "Lỗi kết nối tạm thời với AI service. Vui lòng thử lại sau vài giây.\n\n" +
                 "**Chi tiết:** " + e.getMessage() + "\n\n" +
                 "💡 **Gợi ý:** Hệ thống sẽ tự động retry. Nếu vấn đề tiếp tục, vui lòng liên hệ admin.";
        } else if (e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")) {
          System.out.println("[AiServiceImpl] ❌ Lỗi xác thực API key: " + e.getMessage());
          return "❌ **Lỗi xác thực API**\n\n" +
                 "API key không hợp lệ hoặc đã hết hạn.\n\n" +
                 "**Chi tiết:** " + e.getMessage() + "\n\n" +
                 "💡 **Gợi ý:** Vui lòng kiểm tra lại cấu hình API key.";
        } else if (e.getMessage().contains("429") || e.getMessage().contains("rate limit")) {
          System.out.println("[AiServiceImpl] ⚠️ Rate limit exceeded: " + e.getMessage());
          return "⚠️ **Rate Limit Exceeded**\n\n" +
                 "Đã vượt quá giới hạn số lượng request. Vui lòng chờ vài phút trước khi thử lại.\n\n" +
                 "**Chi tiết:** " + e.getMessage() + "\n\n" +
                 "💡 **Gợi ý:** Hệ thống sẽ tự động retry sau một khoảng thời gian.";
        }
      }
      
      System.out.println("[AiServiceImpl] ❌ ERROR: Failed to parse AI response: " + e.getMessage());
      return "❌ **AI Service Error**\n\n" +
             "Không thể kết nối tới AI service hoặc phản hồi không hợp lệ.\n\n" +
             "**Chi tiết:** " + e.getMessage() + "\n\n" +
             "💡 **Gợi ý:** Vui lòng thử lại sau hoặc liên hệ admin nếu vấn đề tiếp tục.";
    }

    // Đảm bảo query luôn là 1 (bắt buộc tìm kiếm)
    if (requestBody.getQuery() != 1) {
      System.out.println("[AiServiceImpl] Setting query=1 (was " + requestBody.getQuery() + ")");
      requestBody.setQuery(1);
    }

    System.out.println("THong tin quey: "+requestBody.getQuery());
    System.out.println("[AiServiceImpl] Generated query body: " + requestBody.getBody());
    System.out.println("[AiServiceImpl] Using current date context: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    String fixedQuery = requestBody.getBody(); // Giá trị mặc định

    // Validation: Kiểm tra xem body có phải là JSON query hay không
    if (requestBody.getBody() != null) {

      String flg = checkBodyFormat(requestBody);
      System.out.println("flg: " + flg);
      if (flg != null)
      {
        return flg;
      }

      System.out.println("requestBody: " + requestBody);
      System.out.println("chatRequest: " + chatRequest);
      String[] result = getLogData(requestBody, chatRequest);
      content = result[0];
      fixedQuery = result[1];

      System.out.println("content: " + content);

      // Check if content is actually an error message (starts with ❌)
      if (content != null && content.startsWith("❌")) {
        // Return error immediately without further processing
        return content;
      }
      
      // Check if Elasticsearch returned empty results
      if (content != null && isEmptyElasticsearchResult(content)) {
        System.out.println("[AiServiceImpl] Elasticsearch returned no data, continuing with AI processing");
        // Không trả về lỗi trực tiếp, để AI xử lý trường hợp không có dữ liệu
      }
    }

    // Bước 3: Tóm tắt kết quả và trả lời người dùng
    return getAiResponse(sessionId,chatRequest,content, fixedQuery);
  }

  /**
   * Kiểm tra và sửa định dạng phần thân (body) JSON của truy vấn
   * - Cân bằng dấu ngoặc nhọn nếu bị lệch
   * - Xác thực phải có ít nhất một trong hai trường: "query" hoặc "aggs"
   * - Tạo lại RequestBody nếu JSON đã được tự động sửa để đảm bảo nhất quán
   *
   * @param requestBody Đối tượng chứa JSON truy vấn do AI tạo ra
   * @return null nếu hợp lệ; trả về chuỗi thông báo lỗi nếu không hợp lệ
   */
  private String checkBodyFormat(RequestBody requestBody){
    // Chỉ kiểm tra hợp lệ, không tự sửa JSON

    try {
      JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requestBody.getBody());

      // Validate that it's a proper Elasticsearch query
      if (!jsonNode.has("query") && !jsonNode.has("aggs")) {
        System.out.println("[AiServiceImpl] ERROR: Missing 'query' or 'aggs' field!");
        return "❌ AI model trả về query không hợp lệ. Cần có 'query' hoặc 'aggs' field.";
      }

    } catch (Exception e) {
      System.out.println("[AiServiceImpl] ERROR: Invalid JSON format from AI!");
      System.out.println("[AiServiceImpl] Expected: JSON object with 'query' or 'aggs' field");
      System.out.println("[AiServiceImpl] Received: " + requestBody.getBody());
      System.out.println("[AiServiceImpl] Error details: " + e.getMessage());
      return "❌ AI model trả về format không đúng. Cần JSON query (một object duy nhất), nhận được: " + requestBody.getBody();
    }

    return null;
  }


  /**
   * Phiên bản đơn giản của getLogData với retry khi gặp lỗi 400
   * @param requestBody Chứa JSON query từ AI
   * @param chatRequest Yêu cầu gốc của user
   * @return [content, query] - nội dung từ ES và query đã sử dụng
   */
  private String[] getLogData(RequestBody requestBody, ChatRequest chatRequest) {
    String query = requestBody.getBody();

    // First try to fix common query structure issues
    String fixedQuery = fixQueryStructure(query);
    if (!fixedQuery.equals(query)) {
      System.out.println("[AiServiceImpl] 🔧 Query structure was automatically fixed");
      query = fixedQuery; // Use the fixed query
    }

    // Validate query syntax before sending to Elasticsearch
    String validationError = validateQuerySyntax(query);
    if (validationError != null) {
      System.out.println("[AiServiceImpl] Query validation failed: " + validationError);
      return new String[]{
          "❌ **Query Validation Error**\n\n" +
              "Query có cú pháp không hợp lệ trước khi gửi đến Elasticsearch.\n\n" +
              "**Lỗi validation:** " + validationError + "\n\n" +
              "💡 **Gợi ý:** Vui lòng thử câu hỏi khác hoặc kiểm tra lại cấu trúc query.",
          query
      };
    }

    try {
      System.out.println("[AiServiceImpl] Sending query to Elasticsearch: " + query);
      String content = logApiService.search("logs-fortinet_fortigate.log-default*", query);
      System.out.println("[AiServiceImpl] Elasticsearch response received successfully");
      return new String[]{content, query};
    }
    catch (Exception e) {
      System.out.println("[AiServiceImpl] ERROR: Log API returned an error! " + e.getMessage());

      // Parse error details từ Elasticsearch
      String errorDetails = extractElasticsearchError(e.getMessage());
      System.out.println("[AiServiceImpl] Parsed error details: " + errorDetails);

      // Nếu là lỗi 400 Bad Request, thử sửa query bằng AI và retry một lần
      if (e.getMessage().contains("400") || e.getMessage().contains("Bad Request") ||
          e.getMessage().contains("parsing_exception") || e.getMessage().contains("illegal_argument_exception")) {

        System.out.println("[AiServiceImpl] 🔄 Đang thử sửa query với AI và retry...");

        try {
          // Lấy field mapping và tạo comparison prompt với error details
          String allFields = SchemaHint.getSchemaHint();
          String prevQuery = requestBody.getBody();
          String userMess = chatRequest.message();

          // Cải thiện prompt với error details cụ thể
          String enhancedPrompt = PromptTemplate.getComparisonPrompt(
              allFields, prevQuery, userMess, generateDateContext(LocalDateTime.now())
          ) + "\n\nIMPORTANT: The previous query failed with this error:\n" + errorDetails + 
              "\nPlease fix the specific issue mentioned in the error and generate a corrected Elasticsearch query.";

          Prompt comparePrompt = new Prompt(
              new SystemMessage(enhancedPrompt),
              new UserMessage("Fix this query error: " + errorDetails + " | User request: " + userMess + " | Failed query: " + prevQuery)
          );

          // Provider for retry flow: default ChatClient (OpenAI)
          // Giữ temperature = 0.0 để kết quả ổn định và bám sát lỗi cần sửa
          ChatOptions retryChatOptions = ChatOptions.builder()
              .temperature(0.0D)
              .build();

          // Gọi AI để tạo query mới với isolate memory
          String retryConversationId = "retry_" + System.currentTimeMillis();
          String newQuery;
          
          try {
            // First try to get as RequestBody (normal flow)
            RequestBody newRequestBody = chatClient.prompt(comparePrompt)
                .options(retryChatOptions)
                .advisors(advisorSpec -> advisorSpec.param(
                    ChatMemory.CONVERSATION_ID, retryConversationId
                ))
                .call()
                .entity(new ParameterizedTypeReference<>() {});

            // Đảm bảo query luôn là 1
            if (newRequestBody.getQuery() != 1) {
              newRequestBody.setQuery(1);
            }
            newQuery = newRequestBody.getBody();
          } catch (Exception parseException) {
            System.out.println("[AiServiceImpl] Failed to parse as RequestBody, trying raw JSON: " + parseException.getMessage());
            
            // If RequestBody parsing fails, try to get raw JSON response
            String rawResponse = chatClient.prompt(comparePrompt)
                .options(retryChatOptions)
                .advisors(advisorSpec -> advisorSpec.param(
                    ChatMemory.CONVERSATION_ID, retryConversationId
                ))
                .call()
                .content();
            
            // Clean and validate the raw JSON response
            newQuery = rawResponse.trim();
            
            // Remove any markdown code blocks if present
            if (newQuery.startsWith("```json")) {
              newQuery = newQuery.substring(7);
            }
            if (newQuery.endsWith("```")) {
              newQuery = newQuery.substring(0, newQuery.length() - 3);
            }
            newQuery = newQuery.trim();
            
            // Validate that it's valid JSON
            try {
              new com.fasterxml.jackson.databind.ObjectMapper().readTree(newQuery);
              System.out.println("[AiServiceImpl] Successfully parsed raw JSON response");
            } catch (Exception jsonException) {
              System.out.println("[AiServiceImpl] Raw response is not valid JSON: " + jsonException.getMessage());
              throw new RuntimeException("AI returned invalid JSON: " + newQuery, jsonException);
            }
          }
          System.out.println("[AiServiceImpl] 🔧 Generated new query with error fix: " + newQuery);

          // Kiểm tra xem query mới có khác query cũ không
          if (newQuery.equals(prevQuery)) {
            System.out.println("[AiServiceImpl] WARNING: New query is identical to failed query");
            return new String[]{
                "❌ **Elasticsearch Error (Same Query Generated)**\n\n" +
                    "AI tạo ra query giống hệt với query đã lỗi.\n\n" +
                    "**Lỗi gốc:** " + errorDetails + "\n\n" +
                    "💡 **Gợi ý:** Vui lòng thử câu hỏi khác với cách diễn đạt khác.",
                query
            };
          }

          // Retry với query mới
          System.out.println("[AiServiceImpl] 🔄 Đang thử lại với query đã sửa...");
          String retryContent = logApiService.search("logs-fortinet_fortigate.log-default*", newQuery);
          System.out.println("[AiServiceImpl] ✅ Retry successful with corrected query");
          return new String[]{retryContent, newQuery};

        } catch (Exception retryE) {
          System.out.println("[AiServiceImpl] Retry also failed: " + retryE.getMessage());
          
          // Determine if it's a parsing error or Elasticsearch error
          String retryErrorDetails;
          if (retryE.getMessage().contains("Cannot deserialize") || retryE.getMessage().contains("MismatchedInputException")) {
            retryErrorDetails = "AI Response Parsing Error - AI returned invalid format";
          } else {
            retryErrorDetails = extractElasticsearchError(retryE.getMessage());
          }
          
          return new String[]{
              "❌ **Elasticsearch Error (After Retry)**\n\n" +
                  "Query ban đầu lỗi và query được sửa cũng không thành công.\n\n" +
                  "**Lỗi ban đầu:** " + errorDetails + "\n\n" +
                  "**Lỗi sau retry:** " + retryErrorDetails + "\n\n" +
                  "💡 **Gợi ý:** Vui lòng thử câu hỏi khác hoặc kiểm tra cấu trúc dữ liệu.",
              query
          };
        }
      }

      // Với các lỗi khác (không phải 400), trả lỗi trực tiếp
      return new String[]{
          "❌ **Elasticsearch Error**\n\n" +
              "Không thể thực hiện truy vấn Elasticsearch.\n\n" +
              "**Chi tiết lỗi:** " + errorDetails + "\n\n" +
              "💡 **Gợi ý:** Kiểm tra lại câu hỏi hoặc liên hệ admin.",
          query
      };
    }
  }

  /**
   * Kiểm tra xem kết quả từ Elasticsearch có rỗng không
   * @param elasticsearchResponse JSON response từ Elasticsearch
   * @return true nếu không có dữ liệu, false nếu có dữ liệu
   */
  private boolean isEmptyElasticsearchResult(String elasticsearchResponse) {
    try {
      JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(elasticsearchResponse);
      
      boolean hasHits = false;
      if (jsonNode.has("hits")) {
        JsonNode hitsNode = jsonNode.get("hits");
        if (hitsNode.has("total")) {
          JsonNode totalNode = hitsNode.get("total");
          if (totalNode.has("value") && totalNode.get("value").asLong() > 0) {
            hasHits = true;
          }
        }
        if (!hasHits && hitsNode.has("hits")) {
          JsonNode hitsArrayNode = hitsNode.get("hits");
          if (hitsArrayNode.isArray() && hitsArrayNode.size() > 0) {
            hasHits = true;
          }
        }
      }
      
      boolean hasAggregations = jsonNode.has("aggregations");
      
      // No-data ONLY when there are no hits AND no aggregations at all
      return !hasHits && !hasAggregations;
      
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] Error parsing Elasticsearch response for empty check: " + e.getMessage());
      return false; // If parse fails, do not block; let AI format
    }
  }

  /**
   * Parse error message từ Elasticsearch để lấy thông tin chi tiết
   * @param errorMessage Raw error message
   * @return Parsed error details
   */
  private String extractElasticsearchError(String errorMessage) {
    // Extract common Elasticsearch error patterns
    if (errorMessage.contains("parsing_exception")) {
      return "Query syntax error - Invalid JSON structure or field mapping";
    } else if (errorMessage.contains("illegal_argument_exception")) {
      return "Invalid argument - Check field names and aggregation syntax";
    } else if (errorMessage.contains("No mapping found")) {
      return "Field mapping error - Field does not exist in index";
    } else if (errorMessage.contains("400 Bad Request")) {
      return "Bad Request - Query structure or field validation failed";
    } else if (errorMessage.contains("index_not_found_exception")) {
      return "Index not found - Check index name and existence";
    } else {
      // Return first 200 characters của error message
      return errorMessage.length() > 200 ? errorMessage.substring(0, 200) + "..." : errorMessage;
    }
  }

  /**
   * Validate Elasticsearch query syntax before sending to Elasticsearch
   * @param query JSON query string
   * @return null if valid, error message if invalid
   */
  private String validateQuerySyntax(String query) {
    try {
      // Parse JSON to check syntax
      JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(query);
      
      // Check for required fields
      if (!jsonNode.has("query") && !jsonNode.has("aggs")) {
        return "Query must contain either 'query' or 'aggs' field";
      }
      
      // Check for common syntax issues
      if (jsonNode.has("query")) {
        JsonNode queryNode = jsonNode.get("query");
        
        // Check if aggs is incorrectly placed inside query instead of at root level
        if (queryNode.has("aggs")) {
          return "Aggregations must be at root level, not inside query. Move 'aggs' outside of 'query'.";
        }
        
        if (queryNode.has("bool")) {
          JsonNode boolNode = queryNode.get("bool");
          
          // Check if aggs is incorrectly placed inside bool
          if (boolNode.has("aggs")) {
            return "Aggregations must be at root level, not inside bool query. Move 'aggs' outside of 'query'.";
          }
          
          if (boolNode.has("filter")) {
            JsonNode filterNode = boolNode.get("filter");
            if (!filterNode.isArray()) {
              return "Bool filter must be an array";
            }
            
            // Check each filter element
            for (JsonNode filter : filterNode) {
              if (filter.has("aggs")) {
                return "Aggregations cannot be inside filter. Move 'aggs' to root level.";
              }
            }
          }
          
          if (boolNode.has("must")) {
            JsonNode mustNode = boolNode.get("must");
            if (!mustNode.isArray()) {
              return "Bool must must be an array";
            }
          }
          
          if (boolNode.has("should")) {
            JsonNode shouldNode = boolNode.get("should");
            if (!shouldNode.isArray()) {
              return "Bool should must be an array";
            }
          }
        }
      }
      
      // Check aggregations structure
      if (jsonNode.has("aggs")) {
        JsonNode aggsNode = jsonNode.get("aggs");
        if (!aggsNode.isObject()) {
          return "Aggregations must be an object";
        }
      }
      
      // Check for size parameter
      if (jsonNode.has("size")) {
        JsonNode sizeNode = jsonNode.get("size");
        if (!sizeNode.isNumber()) {
          return "Size parameter must be a number";
        }
      }
      
      return null; // Valid query
      
    } catch (Exception e) {
      return "Invalid JSON syntax: " + e.getMessage();
    }
  }

  /**
   * Attempt to fix common Elasticsearch query structure issues
   * @param query JSON query string
   * @return Fixed query string or original if no fixes needed
   */
  private String fixQueryStructure(String query) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(query);
      
      // Check if aggs is incorrectly placed inside query
      if (jsonNode.has("query")) {
        JsonNode queryNode = jsonNode.get("query");
        
        // Fix: Move aggs from inside query to root level
        if (queryNode.has("aggs")) {
          JsonNode aggsNode = queryNode.get("aggs");
          
          // Create a new root object with aggs moved out
          ObjectNode fixedQuery = mapper.createObjectNode();
          fixedQuery.set("query", queryNode.deepCopy());
          fixedQuery.remove("aggs"); // Remove aggs from query
          fixedQuery.set("aggs", aggsNode);
          
          // Copy other root-level fields
          jsonNode.fieldNames().forEachRemaining(fieldName -> {
            if (!fieldName.equals("query") && !fieldName.equals("aggs")) {
              fixedQuery.set(fieldName, jsonNode.get(fieldName));
            }
          });
          
          String fixedQueryString = mapper.writeValueAsString(fixedQuery);
          System.out.println("[AiServiceImpl] Fixed query structure - moved aggs to root level");
          return fixedQueryString;
        }
        
        // Fix: Move aggs from inside bool to root level
        if (queryNode.has("bool")) {
          JsonNode boolNode = queryNode.get("bool");
          if (boolNode.has("aggs")) {
            JsonNode aggsNode = boolNode.get("aggs");
            
            // Create a new root object with aggs moved out
            ObjectNode fixedQuery = mapper.createObjectNode();
            ObjectNode newQueryNode = queryNode.deepCopy();
            ObjectNode newBoolNode = boolNode.deepCopy();
            newBoolNode.remove("aggs");
            newQueryNode.set("bool", newBoolNode);
            fixedQuery.set("query", newQueryNode);
            fixedQuery.set("aggs", aggsNode);
            
            // Copy other root-level fields
            jsonNode.fieldNames().forEachRemaining(fieldName -> {
              if (!fieldName.equals("query") && !fieldName.equals("aggs")) {
                fixedQuery.set(fieldName, jsonNode.get(fieldName));
              }
            });
            
            String fixedQueryString = mapper.writeValueAsString(fixedQuery);
            System.out.println("[AiServiceImpl] Fixed query structure - moved aggs from bool to root level");
            return fixedQueryString;
          }
        }
      }
      
      return query; // No fixes needed
      
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] Failed to fix query structure: " + e.getMessage());
      return query; // Return original query if fix fails
    }
  }

  /**
   * Create and validate an Elasticsearch DSL query
   * @param queryBody JSON query string
   * @return Map containing validation result and formatted query
   */
  public Map<String, Object> createAndValidateQuery(String queryBody) {
    Map<String, Object> result = new HashMap<>();
    
    try {
      // First try to fix common structure issues
      String fixedQuery = fixQueryStructure(queryBody);
      
      // Validate the query syntax
      String validationError = validateQuerySyntax(fixedQuery);
      
      if (validationError != null) {
        result.put("success", false);
        result.put("error", validationError);
        result.put("query", queryBody);
        result.put("fixed_query", fixedQuery);
        return result;
      }
      
      // Format the query for better readability
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(fixedQuery);
      String formattedQuery = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
      
      result.put("success", true);
      result.put("query", fixedQuery);
      result.put("formatted_query", formattedQuery);
      result.put("validation_message", "Query syntax is valid");
      result.put("was_fixed", !fixedQuery.equals(queryBody));
      
    } catch (Exception e) {
      result.put("success", false);
      result.put("error", "Failed to process query: " + e.getMessage());
      result.put("query", queryBody);
    }
    
    return result;
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

    // Lấy thời gian thực của máy
    LocalDateTime currentTime = LocalDateTime.now();
    String currentDate = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String currentDateTime = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Định dạng JSON query để hiển thị tốt hơn
    String formattedQuery = query;
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(query);
      formattedQuery = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] Could not format query JSON: " + e.getMessage());
    }

    // Tạo system message hướng dẫn AI cách phản hồi
    SystemMessage systemMessage = new SystemMessage(String.format("""
                You are HPT.AI
                You should respond in a formal voice.

                IMPORTANT CONTEXT:
                - Current date: %s
                - Current datetime: %s (Vietnam timezone +07:00)
                - All dates in the query and data are valid and current
                - NEVER mention that dates are "in the future" or incorrect
                - NEVER reference 2023 or any other year as current time

                IMPORTANT: Always include the Elasticsearch query used at the end of your response.
                Also include a short justification for key field choices.
                CRITICAL: If the user asks for counts (đếm/số lượng) or totals (tổng), you MUST parse Elasticsearch aggregations and state the numeric answer clearly.

                DATA INTERPRETATION RULES:
                - FIRST: Check if there is ANY data in the response. Look for aggregations with values > 0 OR hits with entries.
                - If aggregations.total_count.value > 0, there IS data - report the count (e.g., "Số log: 1234").
                - If aggregations.total_bytes.value > 0, there IS data - report the total (e.g., "Tổng bytes: 56789").
                - If hits.total.value > 0 OR hits.hits array has entries, there IS data - summarize the logs naturally.
                - ONLY say "Không tìm thấy dữ liệu" if BOTH aggregations are empty/zero AND hits.total.value = 0 AND hits.hits is empty array.
                - DO NOT say no data just because size=0 - aggregations can still have results.
                - If aggregations exist with non-zero values, ALWAYS report them as the answer.
                - If query has "aggs" but no "query", it's an aggregation query - base answer on aggregations only.
                - If both aggregations and hits exist, prioritize aggregations for counts/totals, use hits for details.
                - IMPORTANT: For queries that return hits with data (hits.total.value > 0), ALWAYS summarize the log entries, never say no data.
                - CRITICAL: In the provided content, if you see hits.total.value > 0 and hits.hits array with entries, there IS data - summarize it.
                - ABSOLUTE RULE: If hits.total.value is greater than 0 (like 10000) and hits.hits contains entries (like 10 entries), there IS DEFINITELY data - summarize the logs.
                - NEVER say "no data" when hits.total.value > 0 and hits.hits has entries.
                - If you see "hits":{"total":{"value":10000},"hits":[{...},{...},...]} then there IS data - summarize it.

                NO DATA SCENARIOS (only when truly empty):
                - hits.total.value = 0 AND hits.hits = [] AND no aggregations with values > 0
                - aggregations.total_count.value = 0 AND no other aggregation values > 0

                LOG DATA EXTRACTION RULES:
                For each log entry in hits.hits, extract and display these key fields when available:
                - Người dùng: source.user.name (if available)
                - Địa chỉ nguồn: source.ip
                - Địa chỉ đích: destination.ip
                - Hành động: fortinet.firewall.action (allow/deny) or event.action
                - Nội dung: event.message or log.message or message
                - Thời gian: @timestamp (format as readable date)
                - Rule: rule.name (if available)
                - Port đích: destination.port (if available)
                - Protocol: network.protocol (if available)
                - Bytes: network.bytes (if available)
                - Quốc gia nguồn: source.geo.country_name (if available)
                - Quốc gia đích: destination.geo.country_name (if available)
                - Mức rủi ro: fortinet.firewall.crlevel (if available)
                - Tấn công: fortinet.firewall.attack (if available)

                logData : %s
                query : %s

                Format your response as:
                [Your analysis and summary of the data based on current date %s]

                LOG INFORMATION PRESENTATION:
                Present log information in a natural, descriptive format. For each log entry, write a clear description that includes the key details:

                Format each log entry as a natural description like:
                "Vào lúc [time], từ địa chỉ [source.ip] đã [action] kết nối đến [destination.ip]:[port] sử dụng giao thức [protocol]. Rule được áp dụng: [rule.name]. Dữ liệu truyền tải: [bytes] bytes."

                Include additional details when available:
                - If source.user.name exists: "Người dùng: [source.user.name]"
                - If event.message exists: "Mô tả: [event.message]"
                - If geo information exists: "Từ quốc gia [source.geo.country_name] đến [destination.geo.country_name]"
                - If risk level exists: "Mức rủi ro: [fortinet.firewall.crlevel]"
                - If attack signature exists: "Cảnh báo tấn công: [fortinet.firewall.attack]"

                Present multiple entries in a flowing narrative style, grouping similar activities when appropriate.

                When the question requests:
                - "đếm số log ..." → Output: "Số log: <number>" (derived from aggregations.total_count.value)
                - "tổng log ..." (tổng số bản ghi) → Output: "Tổng log: <number>" (also aggregations.total_count.value)
                - "tổng bytes/packets ..." → Output: "Tổng bytes/packets: <number>" (from aggregations.total_bytes/total_packets.value)

                Field Selection Rationale (concise):
                - Explain why the chosen fields best match the intent, referencing categories when relevant
                - Prefer reasons like: action semantics (fortinet.firewall.action vs event.outcome), traffic volume (network.bytes/packets), direction (network.direction), geo (source/destination.geo.country_name), rule grouping (rule.name vs ruleid), user specificity (source.user.* vs user.*)
                - 3-6 bullets max

                **Elasticsearch Query Used:**
                ```json
                %s
                ```
                """
        ,currentDate, currentDateTime, content, query, currentDate, formattedQuery));

    UserMessage userMessage = new UserMessage(chatRequest.message());
    Prompt prompt = new Prompt(systemMessage, userMessage);

    // Gọi AI với ngữ cảnh cuộc trò chuyện để tạo phản hồi
    // Provider: default ChatClient (OpenAI) for final response generation
    return chatClient
        .prompt(prompt)
        // OpenAI temperature kept at 0.0 for deterministic responses
        .options(ChatOptions.builder().temperature(0.0D).build())
        .advisors(advisorSpec -> advisorSpec.param(
            ChatMemory.CONVERSATION_ID, conversationId
        ))
        .call()
        .content();
  }

  /**
   * Phiên bản đặc biệt của getAiResponse dành cho comparison mode
   * Sử dụng conversationId tùy chỉnh để tránh memory contamination giữa các model
   * 
   * @param conversationId Conversation ID tùy chỉnh (ví dụ: "39_openai", "39_openrouter")
   * @param chatRequest Yêu cầu gốc từ user
   * @param content Dữ liệu từ Elasticsearch
   * @param query Query Elasticsearch đã sử dụng
   * @return Phản hồi từ AI
   */
  public String getAiResponseForComparison(String conversationId, ChatRequest chatRequest, String content, String query) {
    // Lấy thời gian thực của máy
    LocalDateTime currentTime = LocalDateTime.now();
    String currentDate = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String currentDateTime = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Định dạng JSON query để hiển thị tốt hơn
    String formattedQuery = query;
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(query);
      formattedQuery = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] Could not format query JSON: " + e.getMessage());
    }
      System.out.println("[AiServiceImpl] nội dung content: " + content);
    // Tạo system message hướng dẫn AI cách phản hồi
    SystemMessage systemMessage = new SystemMessage(String.format("""
                You are HPT.AI
                You should respond in a formal voice.
                
                IMPORTANT CONTEXT:
                - Current date: %s
                - Current datetime: %s (Vietnam timezone +07:00)
                - All dates in the query and data are valid and current
                - NEVER mention that dates are "in the future" or incorrect
                - NEVER reference 2023 or any other year as current time
                
                IMPORTANT: Always include the Elasticsearch query used at the end of your response.
                Also include a short justification for key field choices.
                CRITICAL: If the user asks for counts (đếm/số lượng) or totals (tổng), you MUST parse Elasticsearch aggregations and state the numeric answer clearly.
                
                DATA INTERPRETATION RULES:
                - FIRST: Check if there is ANY data in the response. Look for aggregations with values > 0 OR hits with entries.
                - If aggregations.total_count.value > 0, there IS data - report the count (e.g., "Số log: 1234").
                - If aggregations.total_bytes.value > 0, there IS data - report the total (e.g., "Tổng bytes: 56789").
                - If hits.total.value > 0 OR hits.hits array has entries, there IS data - summarize the logs naturally.
                - ONLY say "Không tìm thấy dữ liệu" if BOTH aggregations are empty/zero AND hits.total.value = 0 AND hits.hits is empty array.
                - DO NOT say no data just because size=0 - aggregations can still have results.
                - If aggregations exist with non-zero values, ALWAYS report them as the answer.
                - If query has "aggs" but no "query", it's an aggregation query - base answer on aggregations only.
                - If both aggregations and hits exist, prioritize aggregations for counts/totals, use hits for details.
                - IMPORTANT: For queries that return hits with data (hits.total.value > 0), ALWAYS summarize the log entries, never say no data.
                - CRITICAL: In the provided content, if you see hits.total.value > 0 and hits.hits array with entries, there IS data - summarize it.
                - ABSOLUTE RULE: If hits.total.value is greater than 0 (like 10000) and hits.hits contains entries (like 10 entries), there IS DEFINITELY data - summarize the logs.
                - NEVER say "no data" when hits.total.value > 0 and hits.hits has entries.
                - If you see "hits":{"total":{"value":10000},"hits":[{...},{...},...]} then there IS data - summarize it.
                
                NO DATA HANDLING:
                When hits.total.value = 0 or aggregations return empty buckets or zero count:
                - Inform the user that no data was found matching their criteria
                - Suggest possible reasons why no data was found
                - Suggest possible modifications to the query
                - DO NOT use a fixed format, respond naturally
                
                EXAMPLES OF NO DATA SCENARIOS:
                - {"hits":{"total":{"value":0},"hits":[]}} → No matching documents
                - {"aggregations":{"total_count":{"value":0}}} → Count is zero
                - {"aggregations":{"top_users":{"buckets":[]}}} → No aggregation results
                - Empty aggregation buckets = no matching data found
                
                LOG DATA EXTRACTION RULES:
                For each log entry in hits.hits, extract and display these key fields when available:
                - Người dùng: source.user.name (if available)
                - Địa chỉ nguồn: source.ip 
                - Địa chỉ đích: destination.ip
                - Hành động: fortinet.firewall.action (allow/deny) or event.action
                - Nội dung: event.message or log.message or message
                - Thời gian: @timestamp (format as readable date)
                - Rule: rule.name (if available)
                - Port đích: destination.port (if available)
                - Protocol: network.protocol (if available)
                - Bytes: network.bytes (if available)
                - Quốc gia nguồn: source.geo.country_name (if available)
                - Quốc gia đích: destination.geo.country_name (if available)
                - Mức rủi ro: fortinet.firewall.crlevel (if available)
                - Tấn công: fortinet.firewall.attack (if available)
                - Nếu fortinet.firewall.cfgattr tồn tại hoặc câu hỏi liên quan đến CNHN_ZONE/cfgattr:
                  • QUERY PATTERN: {"query":{"bool":{"filter":[{"term":{"source.user.name":"tanln"}},{"match":{"message":"CNHN_ZONE"}}]}},"sort":[{"@timestamp":"asc"}],"size":200}
                  • Phân tích chuỗi cfgattr theo quy tắc:
                    1) Tách hai phần trước và sau "->" thành hai danh sách
                    2) Trước khi tách, loại bỏ tiền tố "interface[" (nếu có) và dấu "]" ở cuối (nếu có)
                    3) Mỗi danh sách tách tiếp bằng dấu phẩy hoặc khoảng trắng, chuẩn hóa và loại bỏ khoảng trắng thừa
                    4) "Thêm" = các giá trị có trong danh sách mới nhưng không có trong danh sách cũ
                    5) "Xóa" = các giá trị có trong danh sách cũ nhưng không có trong danh sách mới
                    
                    VÍ DỤ PHÂN TÍCH:
                    Input: "interface[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab->LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
                    
                    Bước 1: Tách bằng "->"
                    - Trước: "[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab"
                    - Sau: "LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
                    
                    Bước 2: Bỏ tiền tố "interface[" và dấu "]" rồi tách từng danh sách bằng khoảng trắng
                    - Ban đầu: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV, CNHN_Wire_Lab]
                    - Sau: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV
                    
                    Bước 3: So sánh
                    - Thêm: [] (không có)
                    - Xóa: [CNHN_Wire_Lab]
                  • Xuất theo timeline (sắp xếp theo @timestamp):
                    - Thời gian: [@timestamp]
                    - Người dùng: [source.user.name]
                    - IP: [source.ip]
                    - Hành động: [message]
                    - Ban đầu: [...]
                    - Sau: [...]
                    - Thêm: [...]
                    - Xóa: [...]
                    Luôn luôn hiển thị cả Ban đầu và Sau, ngay cả khi không có sự thay đổi.
                  • Nếu không có "->" trong cfgattr, coi toàn bộ là danh sách hiện tại
                logData : %s
                query : %s
                
                Format your response as:
                [Your analysis and summary of the data based on current date %s]
                
                LOG INFORMATION PRESENTATION:
                Present log information in a natural, descriptive format. For each log entry, write a clear description that includes the key details:
                
                Format each log entry as a natural description like:
                "Vào lúc [time], từ địa chỉ [source.ip] đã [action] kết nối đến [destination.ip]:[port] sử dụng giao thức [protocol]. Rule được áp dụng: [rule.name]. Dữ liệu truyền tải: [bytes] bytes."
                
                Include additional details when available:
                - If source.user.name exists: "Người dùng: [source.user.name]"
                - If event.message exists: "Mô tả: [event.message]"
                - If geo information exists: "Từ quốc gia [source.geo.country_name] đến [destination.geo.country_name]"
                - If risk level exists: "Mức rủi ro: [fortinet.firewall.crlevel]"
                - If attack signature exists: "Cảnh báo tấn công: [fortinet.firewall.attack]"
                
                Present multiple entries in a flowing narrative style, grouping similar activities when appropriate.
                
                When the question requests:
                - "đếm số log ..." → Output: "Số log: <number>" (derived from aggregations.total_count.value)
                - "tổng log ..." (tổng số bản ghi) → Output: "Tổng log: <number>" (also aggregations.total_count.value)
                - "tổng bytes/packets ..." → Output: "Tổng bytes/packets: <number>" (from aggregations.total_bytes/total_packets.value)
                
                Field Selection Rationale (concise):
                - Explain why the chosen fields best match the intent, referencing categories when relevant
                - Prefer reasons like: action semantics (fortinet.firewall.action vs event.outcome), traffic volume (network.bytes/packets), direction (network.direction), geo (source/destination.geo.country_name), rule grouping (rule.name vs ruleid), user specificity (source.user.* vs user.*)
                - 3-6 bullets max
                
                **Elasticsearch Query Used:**
                ```json
                %s
                ```
                """
        ,currentDate, currentDateTime, content, query, currentDate, formattedQuery));

    UserMessage userMessage = new UserMessage(chatRequest.message());
    Prompt prompt = new Prompt(systemMessage, userMessage);
    // Gọi AI với conversation ID tùy chỉnh để tránh memory contamination
    // Provider: default ChatClient (OpenAI) for comparison response generation
    return chatClient
        .prompt(prompt)
        // OpenAI temperature kept at 0.0 for deterministic responses
        .options(ChatOptions.builder().temperature(0.0D).build())
        .advisors(advisorSpec -> advisorSpec.param(
            ChatMemory.CONVERSATION_ID, conversationId
        ))
        .call()
        .content();
  }

  /**
   * Xử lý yêu cầu của người dùng trong chế độ so sánh, sử dụng cả OpenAI và OpenRouter
   * @param sessionId ID phiên chat để duy trì ngữ cảnh
   * @param chatRequest Yêu cầu từ người dùng
   * @return Kết quả so sánh giữa hai provider
   */
  @Override
  public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
    Map<String, Object> result = new HashMap<>();
    LocalDateTime now = LocalDateTime.now();
    String dateContext = generateDateContext(now);
    
    try {
      System.out.println("[AiServiceImpl] ===== BẮT ĐẦU CHẾ ĐỘ SO SÁNH =====");
      System.out.println("[AiServiceImpl] Bắt đầu chế độ so sánh cho phiên: " + sessionId);
      System.out.println("[AiServiceImpl] Tin nhắn người dùng: " + chatRequest.message());
      System.out.println("[AiServiceImpl] Sử dụng ngữ cảnh ngày tháng: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
      
      // --- BƯỚC 1: So sánh quá trình tạo query ---
      System.out.println("[AiServiceImpl] ===== BƯỚC 1: Tạo Elasticsearch Query =====");
      
      // Chuẩn bị schema một lần để dùng lại cho cả hai prompt
      String fullSchema = SchemaHint.getSchemaHint();
      
      // Sử dụng QueryPromptTemplate: đưa toàn bộ thư viện + ví dụ động (nếu có)
      Map<String, Object> dynamicInputs = new HashMap<>();
      String queryPrompt = com.example.chatlog.utils.QueryPromptTemplate.createQueryGenerationPromptWithAllTemplates(
          chatRequest.message(),
          dateContext,
          fullSchema,
          SchemaHint.getRoleNormalizationRules(),
          dynamicInputs
      );
      // System.out.println("[AiServiceImpl] SYSTEM PROMPT (queryPrompt) length=" + queryPrompt.length());
      // System.out.println("[AiServiceImpl] SYSTEM PROMPT (queryPrompt) preview:\n" + queryPrompt);

      // Tạo system prompt đầy đủ từ PromptTemplate với toàn bộ SchemaHint để bổ sung ngữ cảnh
      String fullSystemPrompt = com.example.chatlog.utils.PromptTemplate.getSystemPrompt(
          dateContext,
          SchemaHint.getRoleNormalizationRules(),
          fullSchema,
          SchemaHint.getCategoryGuides(),
          SchemaHint.getNetworkTrafficExamples(),
          SchemaHint.getIPSSecurityExamples(),
          SchemaHint.getAdminRoleExample(),
          SchemaHint.getGeographicExamples(),
          SchemaHint.getFirewallRuleExamples(),
          SchemaHint.getCountingExamples(),
          SchemaHint.getQuickPatterns()
      );

      // Ghép tất cả vào một system message duy nhất để AI có tối đa bối cảnh
      String combinedPrompt = queryPrompt + "\n\n" + fullSystemPrompt;
      SystemMessage systemMessage = new SystemMessage(combinedPrompt);

      UserMessage userMessage = new UserMessage(chatRequest.message());
      List<String> schemaHints = SchemaHint.allSchemas();
      String schemaContext = String.join("\n\n", schemaHints);
      UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);
      // Provide a single sample log to help AI infer fields and structure
      UserMessage sampleLogMsg = new UserMessage("SAMPLE LOG (for inference):\n" + SchemaHint.examplelog());

      System.out.println("---------------------------------------------------------------------------------------");
      Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, sampleLogMsg, userMessage));

      ChatOptions chatOptions = ChatOptions.builder()
          .temperature(0.0D)
          .build();
      
      // Theo dõi thời gian tạo query của OpenAI
      System.out.println("[AiServiceImpl] 🔵 OPENAI - Đang tạo Elasticsearch query...");
      long openaiStartTime = System.currentTimeMillis();
      RequestBody openaiQuery = chatClient
          .prompt(prompt)
          .options(chatOptions)
          .call()
          .entity(new ParameterizedTypeReference<>() {});
      long openaiEndTime = System.currentTimeMillis();
      
      // Đảm bảo giá trị query được đặt là 1
      if (openaiQuery.getQuery() != 1) {
        openaiQuery.setQuery(1);
      }
      
      // Sửa query OpenAI nếu cần
      String openaiQueryString = openaiQuery.getBody();
      System.out.println("[AiServiceImpl] ✅ OPENAI - Query được tạo thành công trong " + (openaiEndTime - openaiStartTime) + "ms");
      System.out.println("[AiServiceImpl] 📝 OPENAI - Query: " + openaiQueryString);
      
      // Theo dõi thời gian tạo query của OpenRouter (thực sự gọi OpenRouter với temperature khác)
      System.out.println("[AiServiceImpl] 🟠 OPENROUTER - Đang tạo Elasticsearch query...");
      long openrouterStartTime = System.currentTimeMillis();
      
      // Provider: OpenRouter (query generation in comparison mode)
      // Ghi chú: Đây là cấu hình temperature dành cho OpenRouter
      ChatOptions openrouterChatOptions = ChatOptions.builder()
          .temperature(0.5D)
          .build();
      
      RequestBody openrouterQuery;
      String openrouterQueryString;
      
      try {
        // Gọi trực tiếp ChatClient với options OpenRouter (openrouterChatOptions)
        openrouterQuery = chatClient
            .prompt(prompt)
            .options(openrouterChatOptions)
            .call()
            .entity(new ParameterizedTypeReference<>() {});
            
        if (openrouterQuery.getQuery() != 1) {
          openrouterQuery.setQuery(1);
        }
        openrouterQueryString = openrouterQuery.getBody();
        
        System.out.println("[AiServiceImpl] ✅ OPENROUTER - Query được tạo thành công với temperature khác biệt");
      } catch (Exception e) {
        if (e.getMessage() != null && (e.getMessage().contains("503") || e.getMessage().contains("upstream connect error"))) {
          System.out.println("[AiServiceImpl] ⚠️ OPENROUTER - Service tạm thời không khả dụng (HTTP 503), dùng lại query OpenAI: " + e.getMessage());
        } else {
          System.out.println("[AiServiceImpl] ❌ OPENROUTER - Tạo query thất bại, dùng lại query OpenAI: " + e.getMessage());
        }
        openrouterQueryString = openaiQueryString; // Fallback to OpenAI query
      }
      
      long openrouterEndTime = System.currentTimeMillis();
      System.out.println("[AiServiceImpl] ✅ OPENROUTER - Query được tạo trong " + (openrouterEndTime - openrouterStartTime) + "ms");
      System.out.println("[AiServiceImpl] 📝 OPENROUTER - Query: " + openrouterQueryString);
      
      // Lưu trữ kết quả tạo query
      Map<String, Object> queryGenerationComparison = new HashMap<>();
      
      Map<String, Object> openaiGeneration = new HashMap<>();
      openaiGeneration.put("response_time_ms", openaiEndTime - openaiStartTime);
      openaiGeneration.put("model", ModelProvider.OPENAI.getModelName());
      openaiGeneration.put("query", openaiQueryString);
      
      Map<String, Object> openrouterGeneration = new HashMap<>();
      openrouterGeneration.put("response_time_ms", openrouterEndTime - openrouterStartTime);
      openrouterGeneration.put("model", ModelProvider.OPENROUTER.getModelName());
      openrouterGeneration.put("query", openrouterQueryString);
      
      queryGenerationComparison.put("openai", openaiGeneration);
      queryGenerationComparison.put("openrouter", openrouterGeneration);
      
      // --- BƯỚC 2: Tìm kiếm Elasticsearch ---
      System.out.println("[AiServiceImpl] ===== BƯỚC 2: Tìm kiếm Elasticsearch =====");
      
      // Thực hiện tìm kiếm Elasticsearch với cả hai query
      Map<String, Object> elasticsearchComparison = new HashMap<>();
      
      // Tìm kiếm OpenAI
      System.out.println("[AiServiceImpl] 🔵 OPENAI - Đang thực hiện tìm kiếm Elasticsearch...");
        String[] openaiResults = getLogData(openaiQuery, chatRequest);
      String openaiContent = openaiResults[0];
      String finalOpenaiQuery = openaiResults[1];

      // Kiểm tra nếu có lỗi trong quá trình tìm kiếm
      if (openaiContent != null && openaiContent.startsWith("❌")) {
        System.out.println("[AiServiceImpl] ❌ OPENAI - Tìm kiếm Elasticsearch gặp lỗi, đang thử sửa query...");
        System.out.println("[AiServiceImpl] 🔧 OPENAI - Đang tạo lại query với thông tin lỗi...");
      } else {
        System.out.println("[AiServiceImpl] ✅ OPENAI - Tìm kiếm Elasticsearch hoàn thành thành công");
        // Hiển thị preview dữ liệu trả về
        System.out.println("[AiServiceImpl] 📊 DỮ LIỆU TRẢ VỀ (OpenAI): " + (openaiContent.length() > 500 ? openaiContent.substring(0, 500) + "..." : openaiContent));
      }
      
      Map<String, Object> openaiElasticsearch = new HashMap<>();
      openaiElasticsearch.put("data", openaiContent);
      openaiElasticsearch.put("success", true);
      openaiElasticsearch.put("query", finalOpenaiQuery);

      System.out.println("OpenaiElasticsearch : "+ openaiElasticsearch);
      
      // Nếu OpenAI query thất bại hoàn toàn, dùng fallback mẫu gợi ý từ QueryTemplates
      if (openaiContent != null && openaiContent.startsWith("❌")) {
        System.out.println("[AiServiceImpl] 🔵 OPENAI - Dùng fallback query mẫu từ QueryTemplates.OUTBOUND_PORT_ANALYSIS");
        RequestBody fallbackOpenAi = new RequestBody(QueryTemplates.OUTBOUND_PORT_ANALYSIS, 1);
        String[] fallbackOpenAiResults = getLogData(fallbackOpenAi, chatRequest);
        if (fallbackOpenAiResults[0] != null && !fallbackOpenAiResults[0].startsWith("❌")) {
          openaiContent = fallbackOpenAiResults[0];
          finalOpenaiQuery = fallbackOpenAiResults[1];
          System.out.println("[AiServiceImpl] 🔵 OPENAI - Fallback query trả về dữ liệu thành công");
          // Hiển thị preview dữ liệu fallback
          System.out.println("[AiServiceImpl] 📊 DỮ LIỆU FALLBACK (OpenAI): " + (openaiContent.length() > 500 ? openaiContent.substring(0, 500) + "..." : openaiContent));
        }
      }

      // Tìm kiếm OpenRouter (sử dụng query riêng từ OpenRouter)
      System.out.println("[AiServiceImpl] 🟠 OPENROUTER - Đang thực hiện tìm kiếm Elasticsearch...");
      RequestBody openrouterRequestBody = new RequestBody(openrouterQueryString, 1);
      String[] openrouterResults = getLogData(openrouterRequestBody, chatRequest);
      String openrouterContent = openrouterResults[0];
      String finalOpenrouterQuery = openrouterResults[1];
      
      // Kiểm tra nếu có lỗi trong quá trình tìm kiếm
      if (openrouterContent != null && openrouterContent.startsWith("❌")) {
        System.out.println("[AiServiceImpl] ❌ OPENROUTER - Tìm kiếm Elasticsearch gặp lỗi, đang thử sửa query...");
        System.out.println("[AiServiceImpl] 🔧 OPENROUTER - Đang tạo lại query với thông tin lỗi...");
      } else {
        System.out.println("[AiServiceImpl] ✅ OPENROUTER - Tìm kiếm Elasticsearch hoàn thành thành công");
        // Hiển thị preview dữ liệu trả về
        System.out.println("[AiServiceImpl] 📊 DỮ LIỆU TRẢ VỀ (OpenRouter): " + (openrouterContent.length() > 500 ? openrouterContent.substring(0, 500) + "..." : openrouterContent));
      }
      
      Map<String, Object> openrouterElasticsearch = new HashMap<>();
      openrouterElasticsearch.put("data", openrouterContent);
      openrouterElasticsearch.put("success", true);
      openrouterElasticsearch.put("query", finalOpenrouterQuery);

      System.out.println("OpenrouterElasticsearch : " + openrouterElasticsearch);

      // Nếu OpenRouter cũng thất bại, thử fallback tương tự
      if (openrouterContent != null && openrouterContent.startsWith("❌")) {
        System.out.println("[AiServiceImpl] 🟠 OPENROUTER - Dùng fallback query mẫu từ QueryTemplates.OUTBOUND_PORT_ANALYSIS");
        RequestBody fallbackOpenrouter = new RequestBody(QueryTemplates.OUTBOUND_PORT_ANALYSIS, 1);
        String[] fallbackOpenrouterResults = getLogData(fallbackOpenrouter, chatRequest);
        if (fallbackOpenrouterResults[0] != null && !fallbackOpenrouterResults[0].startsWith("❌")) {
          openrouterContent = fallbackOpenrouterResults[0];
          finalOpenrouterQuery = fallbackOpenrouterResults[1];
          System.out.println("[AiServiceImpl] 🟠 OPENROUTER - Fallback query trả về dữ liệu thành công");
          // Hiển thị preview dữ liệu fallback
          System.out.println("[AiServiceImpl] 📊 DỮ LIỆU FALLBACK (OpenRouter): " + (openrouterContent.length() > 500 ? openrouterContent.substring(0, 500) + "..." : openrouterContent));
        }
      }

      elasticsearchComparison.put("openai", openaiElasticsearch);
      elasticsearchComparison.put("openrouter", openrouterElasticsearch);
      
      // --- BƯỚC 3: Tạo câu trả lời ---
      System.out.println("[AiServiceImpl] ===== BƯỚC 3: Tạo câu trả lời AI =====");
      
      // Tạo câu trả lời từ cả hai model
      Map<String, Object> responseGenerationComparison = new HashMap<>();
      
      // Câu trả lời từ OpenAI
      System.out.println("[AiServiceImpl] 🔵 OPENAI - Đang tạo phản hồi từ dữ liệu Elasticsearch...");
      long openaiResponseStartTime = System.currentTimeMillis();
      String openaiResponse = getAiResponseForComparison(sessionId + "_openai", chatRequest, openaiContent, finalOpenaiQuery);
      long openaiResponseEndTime = System.currentTimeMillis();
      System.out.println("[AiServiceImpl] ✅ OPENAI - Phản hồi được tạo thành công trong " + (openaiResponseEndTime - openaiResponseStartTime) + "ms");
      
      Map<String, Object> openaiResponseData = new HashMap<>();
      openaiResponseData.put("elasticsearch_query", finalOpenaiQuery);
      openaiResponseData.put("response", openaiResponse);
      openaiResponseData.put("model", ModelProvider.OPENAI.getModelName());
      openaiResponseData.put("elasticsearch_data", openaiContent);
      openaiResponseData.put("response_time_ms", openaiResponseEndTime - openaiResponseStartTime);
      
      // Câu trả lời từ OpenRouter (sử dụng dữ liệu riêng từ OpenRouter query)
      System.out.println("[AiServiceImpl] 🟠 OPENROUTER - Đang tạo phản hồi từ dữ liệu Elasticsearch...");
      long openrouterResponseStartTime = System.currentTimeMillis();
      String openrouterResponse = getAiResponseForComparison(sessionId + "_openrouter", chatRequest, openrouterContent, finalOpenrouterQuery);
      long openrouterResponseEndTime = System.currentTimeMillis();
      System.out.println("[AiServiceImpl] ✅ OPENROUTER - Phản hồi được tạo thành công trong " + (openrouterResponseEndTime - openrouterResponseStartTime) + "ms");
      
      Map<String, Object> openrouterResponseData = new HashMap<>();
      openrouterResponseData.put("elasticsearch_query", finalOpenrouterQuery);
      openrouterResponseData.put("response", openrouterResponse);
      openrouterResponseData.put("model", ModelProvider.OPENROUTER.getModelName());
      openrouterResponseData.put("elasticsearch_data", openrouterContent);
      openrouterResponseData.put("response_time_ms", openrouterResponseEndTime - openrouterResponseStartTime);
      
      responseGenerationComparison.put("openai", openaiResponseData);
      responseGenerationComparison.put("openrouter", openrouterResponseData);
      
      // --- Tổng hợp kết quả cuối cùng ---
      System.out.println("[AiServiceImpl] ===== TỔNG HỢP KẾT QUẢ =====");
      
      result.put("elasticsearch_comparison", elasticsearchComparison);
      result.put("success", true);
      result.put("query_generation_comparison", queryGenerationComparison);
      result.put("response_generation_comparison", responseGenerationComparison);
      result.put("timestamp", now.toString());
      result.put("user_question", chatRequest.message());
      
      System.out.println("[AiServiceImpl] 🎉 So sánh hoàn thành thành công!");
      System.out.println("[AiServiceImpl] ⏱️ Tổng thời gian OpenAI: " + (openaiEndTime - openaiStartTime + openaiResponseEndTime - openaiResponseStartTime) + "ms");
      System.out.println("[AiServiceImpl] ⏱️ Tổng thời gian OpenRouter: " + (openrouterEndTime - openrouterStartTime + openrouterResponseEndTime - openrouterResponseStartTime) + "ms");
      System.out.println("[AiServiceImpl] 🔍 Sự khác biệt query OpenAI vs OpenRouter: " + (!openaiQueryString.equals(openrouterQueryString) ? "Các query khác nhau được tạo" : "Cùng query được tạo"));
      System.out.println("[AiServiceImpl] 📊 Sự khác biệt dữ liệu OpenAI vs OpenRouter: " + (!openaiContent.equals(openrouterContent) ? "Dữ liệu khác nhau được truy xuất" : "Cùng dữ liệu được truy xuất"));
      
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] ❌ ===== LỖI TRONG CHẾ ĐỘ SO SÁNH =====");
      System.out.println("[AiServiceImpl] 💥 Lỗi trong chế độ so sánh: " + e.getMessage());
      e.printStackTrace();
      
      result.put("success", false);
      result.put("error", e.getMessage());
      result.put("timestamp", now.toString());
    }
    
    return result;
  }
}