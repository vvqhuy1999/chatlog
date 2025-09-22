package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.service.ModelConfigService;
import com.example.chatlog.utils.SchemaHint;
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

  // ObjectMapper đã loại bỏ vì không cần thiết trong phiên bản đơn giản

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
    // System.out.println("[SpringAI] OpenAI URL (from env): " + openaiUrl);
    // System.out.println("[SpringAI] OpenAI API Key (from env): " + openaiApiKey);
    // System.out.println("[SpringAI] Model: " + model);
    // System.out.println("[SpringAI] Request body: " + (chatRequest != null ? chatRequest.message() : "null"));
    // Nếu có cấu hình từ application.yaml thì log ra luôn
    try {
      String baseUrl = System.getProperty("spring.ai.openai.base-url");
      String apiKey = System.getProperty("spring.ai.openai.api-key");
      // System.out.println("[SpringAI] OpenAI base-url (from properties): " + baseUrl);
      // System.out.println("[SpringAI] OpenAI api-key (from properties): " + apiKey);
    } catch (Exception ex) {
//      System.out.println("[SpringAI] Không lấy được base-url/api-key từ properties: " + ex.getMessage());
      System.out.println("[SpringAI] Không lấy được base-url/api-key ");
    }

    // Bước 1: Tạo system message hướng dẫn AI phân tích yêu cầu
    // Lấy ngày hiện tại để AI có thể xử lý các yêu cầu về thời gian chính xác
    LocalDateTime now = LocalDateTime.now();
//        System.out.println(now);
    String dateContext = generateDateContext(now);
//        System.out.println(dateContext);
    SystemMessage systemMessage = new SystemMessage(
        PromptTemplate.getSystemPrompt(
            dateContext,
            SchemaHint.getRoleNormalizationRules(),
            SchemaHint.getCategoryGuides(),
            SchemaHint.getNetworkTrafficExamples(),
            SchemaHint.getIPSSecurityExamples(),
            SchemaHint.getAdminRoleExample(),
            SchemaHint.getGeographicExamples(),
            SchemaHint.getFirewallRuleExamples(),
            SchemaHint.getCountingExamples(),
            SchemaHint.getSchemaHint(),
            SchemaHint.getQuickPatterns()
        )
    );

    List<String> schemaHints = SchemaHint.allSchemas();
    String schemaContext = String.join("\n\n", schemaHints);
    UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);

    UserMessage userMessage = new UserMessage(chatRequest.message());
    // System.out.println(systemMessage);
//    System.out.println("----------------------------------------------------------");
    // System.out.println(schemaMsg);
//    System.out.println("----------------------------------------------------------");
    // System.out.println(userMessage);
    System.out.println("----------------------------------------------------------");
    Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, userMessage));

    // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định và tuân thủ strict
    ChatOptions chatOptions = ChatOptions.builder()
        .temperature(0.0D)
        .build();

    // Gọi AI để phân tích và tạo request body
    try {
      // Log thông tin request gửi tới OpenAI
      // System.out.println("[SpringAI] Prompt: " + prompt);
      // System.out.println("[SpringAI] ChatOptions: " + chatOptions);

      // Nếu muốn log chi tiết HTTP, có thể bật debug cho WebClient/RestTemplate hoặc log thủ công
      // Log giả lập: headers, endpoint, body
//      System.out.println("[SpringAI] --- HTTP REQUEST ---");
//      System.out.println("POST " + (openaiUrl != null ? openaiUrl : "https://api.openai.com/v1/chat/completions"));
//      System.out.println("Headers:");
//      System.out.println("Authorization: Bearer " + (openaiApiKey != null ? openaiApiKey : "(from config)"));
//      System.out.println("Content-Type: application/json");
//      System.out.println("Body:");
//      System.out.println(prompt);
//      System.out.println("---------------------------");

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
    } catch (Exception e) {
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
          String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
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

          ChatOptions retryChatOptions = ChatOptions.builder()
              .temperature(0.2D)  // Tăng temperature để có query khác biệt
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
                - If aggregations.total_count.value exists, that is the count of documents.
                - If aggregations.total_bytes.value (or total_packets.value) exists, that is the total metric.
                - If size:0 with only aggregations is returned, base your answer on aggregations instead of hits.
                - If both count and total are present, report both. If only count is present, report count. If no aggregations, use hits.hits length for count (if applicable).
                
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
    return chatClient
        .prompt(prompt)
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
                - If aggregations.total_count.value exists, that is the count of documents.
                - If aggregations.total_bytes.value (or total_packets.value) exists, that is the total metric.
                - If size:0 with only aggregations is returned, base your answer on aggregations instead of hits.
                - If both count and total are present, report both. If only count is present, report count. If no aggregations, use hits.hits length for count (if applicable).
                
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

    // Gọi AI với conversation ID tùy chỉnh để tránh memory contamination
    return chatClient
        .prompt(prompt)
        .options(ChatOptions.builder().temperature(0.0D).build())
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
      
      // Thiết lập system message cho việc tạo query
      SystemMessage systemMessage = new SystemMessage(
          PromptTemplate.getSystemPrompt(
              dateContext,
              SchemaHint.getRoleNormalizationRules(),
              SchemaHint.getSchemaHint(),
              SchemaHint.getCategoryGuides(),
              SchemaHint.getNetworkTrafficExamples(),
              SchemaHint.getIPSSecurityExamples(),
              SchemaHint.getAdminRoleExample(),
              SchemaHint.getGeographicExamples(),
              SchemaHint.getFirewallRuleExamples(),
              SchemaHint.getCountingExamples(),
              SchemaHint.getQuickPatterns()
          )
      );
      
      UserMessage userMessage = new UserMessage(chatRequest.message());
      List<String> schemaHints = SchemaHint.allSchemas();
      String schemaContext = String.join("\n\n", schemaHints);
      UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);
      
      // System.out.println(systemMessage);
      // System.out.println("---------------------------------------------------------------------------------------");
      // System.out.println(schemaMsg);
      // System.out.println("---------------------------------------------------------------------------------------");
      // System.out.println(userMessage);
      System.out.println("---------------------------------------------------------------------------------------");
      Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, userMessage));
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
      
      // Tạo ChatOptions khác biệt cho OpenRouter (temperature khác để có sự khác biệt)
      ChatOptions openrouterChatOptions = ChatOptions.builder()
          .temperature(0.0D)  // Temperature khác để tạo sự khác biệt
          .build();
      
      RequestBody openrouterQuery;
      String openrouterQueryString;
      
      try {
        // Gọi trực tiếp ChatClient với options khác để mô phỏng OpenRouter
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
      }
      
      Map<String, Object> openaiElasticsearch = new HashMap<>();
      openaiElasticsearch.put("data", openaiContent);
      openaiElasticsearch.put("success", true);
      openaiElasticsearch.put("query", finalOpenaiQuery);
      
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
      }
      
      Map<String, Object> openrouterElasticsearch = new HashMap<>();
      openrouterElasticsearch.put("data", openrouterContent);
      openrouterElasticsearch.put("success", true);
      openrouterElasticsearch.put("query", finalOpenrouterQuery);
      
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