package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.PromptTemplate;
import com.example.chatlog.utils.SchemaHint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

@Service
public class AiServiceImpl implements AiService {
  // Lưu trữ thông tin mapping của Elasticsearch index để tránh gọi lại nhiều lần
  private static String fieldLog;

  // Client để giao tiếp với AI model (Spring AI)
  private final ChatClient chatClient;

  @Autowired
  private LogApiService logApiService;

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
      // Đổi sang sử dụng _field_caps thay vì _mapping để gọn nhẹ
      fieldLog = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
    }
    return fieldLog;
  }

  /**
   * Tạo ngữ cảnh thời gian tối ưu cho AI - chỉ cung cấp thời gian hiện tại
   * AI sẽ tự tính toán các khoảng thời gian cần thiết
   * @param now Thời điểm hiện tại (real-time)
   * @return Chuỗi ngữ cảnh thời gian ngắn gọn
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

    UserMessage userMessage = new UserMessage(chatRequest.message());
    System.out.println(userMessage);
    System.out.println("----------------------------------------------------------");
    Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

    System.out.println("Promt very long: " + prompt);

    // DEBUG: Kiểm tra kích thước field mappings
    // System.out.println("=== FIELD MAPPING DEBUG ===");
    // String fullFieldLog = getFieldLog();
    // System.out.println("getAllField(cached via getFieldLog) length: " + fullFieldLog.length() + " characters");
    // System.out.println("getAllField(cached) content: " + fullFieldLog);

    // String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
    // System.out.println("getAllField() length: " + allFields.length() + " characters");
    // System.out.println("getAllField() content: " + allFields);
    // System.out.println("=== END DEBUG ===");

    // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định và tuân thủ strict
    ChatOptions chatOptions = ChatOptions.builder()
        .temperature(0.2D)
        .build();

    // Gọi AI để phân tích và tạo request body
    try {
      requestBody =  chatClient
          .prompt(prompt)
          .options(chatOptions)
          .call()
          .entity(new ParameterizedTypeReference<>() {
          });
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] ERROR: Failed to parse AI response: " + e.getMessage());
      return "❌ AI model trả về format không hợp lệ. Vui lòng yêu cầu AI xuất đúng JSON Elasticsearch theo hướng dẫn.";
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
    // Không tự sửa JSON; chỉ kiểm tra hợp lệ

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

    try {
      System.out.println("[AiServiceImpl] Sending query to Elasticsearch: " + query);
      String content = logApiService.search("logs-fortinet_fortigate.log-default*", query);
      System.out.println("[AiServiceImpl] Elasticsearch response received successfully");
      return new String[]{content, query};
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] ERROR: Log API returned an error! " + e.getMessage());

      // Nếu là lỗi 400 Bad Request, thử sửa query bằng AI và retry một lần
      if (e.getMessage().contains("400") || e.getMessage().contains("Bad Request") ||
          e.getMessage().contains("parsing_exception") || e.getMessage().contains("illegal_argument_exception")) {

        System.out.println("[AiServiceImpl] Attempting to fix query with AI and retry...");

        try {
          // Lấy field mapping và tạo comparison prompt
          String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
          String prevQuery = requestBody.getBody();
          String userMess = chatRequest.message();

          String systemMsg = PromptTemplate.getComparisonPrompt(
              allFields, prevQuery, userMess, generateDateContext(LocalDateTime.now())
          );

          Prompt comparePrompt = new Prompt(
              new SystemMessage(systemMsg),
              new UserMessage("User request: " + userMess + " | Previous query: " + prevQuery)
          );

          ChatOptions retryChatOptions = ChatOptions.builder()
              .temperature(0.2D)
              .build();

          // Gọi AI để tạo query mới
          RequestBody newRequestBody = chatClient.prompt(comparePrompt)
              .options(retryChatOptions)
              .call()
              .entity(new ParameterizedTypeReference<>() {});

          // Đảm bảo query luôn là 1
          if (newRequestBody.getQuery() != 1) {
            newRequestBody.setQuery(1);
          }

          String newQuery = newRequestBody.getBody();
          System.out.println("[AiServiceImpl] Generated new query: " + newQuery);

          // Retry với query mới
          String retryContent = logApiService.search("logs-fortinet_fortigate.log-default*", newQuery);
          System.out.println("[AiServiceImpl] Retry successful with new query");
          return new String[]{retryContent, newQuery};

        } catch (Exception retryE) {
          System.out.println("[AiServiceImpl] Retry also failed: " + retryE.getMessage());
          return new String[]{
              "❌ **Elasticsearch Error (After Retry)**\n\n" +
                  "Query ban đầu lỗi và query được sửa cũng không thành công.\n\n" +
                  "**Lỗi ban đầu:** " + e.getMessage() + "\n\n" +
                  "**Lỗi sau retry:** " + retryE.getMessage() + "\n\n" +
                  "💡 **Gợi ý:** Vui lòng thử câu hỏi khác hoặc kiểm tra cấu trúc dữ liệu.",
              query
          };
        }
      }

      // Với các lỗi khác (không phải 400), trả lỗi trực tiếp
      return new String[]{
          "❌ **Elasticsearch Error**\n\n" +
              "Không thể thực hiện truy vấn Elasticsearch.\n\n" +
              "**Chi tiết lỗi:** " + e.getMessage() + "\n\n" +
              "💡 **Gợi ý:** Kiểm tra lại câu hỏi hoặc liên hệ admin.",
          query
      };
    }
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

    // Format JSON query for better display
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
        .options(ChatOptions.builder().temperature(0.2D).build())
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