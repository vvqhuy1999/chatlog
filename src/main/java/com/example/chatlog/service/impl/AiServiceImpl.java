package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.SchemaHint;
import com.example.chatlog.utils.PromptTemplate;
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
      fieldLog = logApiService.getFieldLog("logs-fortinet_fortigate.log-default*");
    }
    return fieldLog;
  }

  /**
   * Tạo chuỗi ngày tháng và thời gian cho system message với tính toán chi tiết từ thời gian thực
   * @param now Thời điểm hiện tại (real-time)
   * @return Chuỗi chứa thông tin về các ngày và thời gian để sử dụng trong prompt
   */
  private String generateDateContext(LocalDateTime now) {
    // Format thời gian hiện tại chính xác
    String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String currentDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

    // Tính toán thời gian theo phút (real-time calculation)
    String fiveMinutesAgo = now.minusMinutes(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String tenMinutesAgo = now.minusMinutes(10).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String fifteenMinutesAgo = now.minusMinutes(15).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String thirtyMinutesAgo = now.minusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String fortyFiveMinutesAgo = now.minusMinutes(45).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Tính toán thời gian theo giờ (real-time calculation)
    String oneHourAgo = now.minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String twoHoursAgo = now.minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String threeHoursAgo = now.minusHours(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String sixHoursAgo = now.minusHours(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String twelveHoursAgo = now.minusHours(12).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String twentyFourHoursAgo = now.minusHours(24).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Các mốc ngày
    String yesterday = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String twoDaysAgo = now.minusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String threeDaysAgo = now.minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String fourDaysAgo = now.minusDays(4).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String fiveDaysAgo = now.minusDays(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String oneWeekAgo = now.minusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // Thời gian buổi trong ngày với ngày hiện tại
    String todayMorning = now.withHour(6).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String todayAfternoon = now.withHour(12).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String todayEvening = now.withHour(18).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String todayNight = now.withHour(22).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Thời gian buổi của ngày hôm qua
    String yesterdayMorning = now.minusDays(1).withHour(6).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String yesterdayAfternoon = now.minusDays(1).withHour(12).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String yesterdayEvening = now.minusDays(1).withHour(18).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String yesterdayNight = now.minusDays(1).withHour(22).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    return String.format("""
                REAL-TIME CONTEXT (Vietnam timezone +07:00):
                Current exact time: %s (+07:00)
                Current date: %s
                Current time: %s
                
                MINUTE-BASED RANGES (real-time calculation):
                - "5 phút qua", "last 5 minutes" → from %s+07:00 to %s+07:00
                - "10 phút qua", "last 10 minutes" → from %s+07:00 to %s+07:00
                - "15 phút qua", "last 15 minutes" → from %s+07:00 to %s+07:00
                - "30 phút qua", "last 30 minutes" → from %s+07:00 to %s+07:00
                - "45 phút qua", "last 45 minutes" → from %s+07:00 to %s+07:00
                
                HOUR-BASED RANGES (real-time calculation):
                - "1 giờ qua", "last 1 hour" → from %s+07:00 to %s+07:00
                - "2 giờ qua", "last 2 hours" → from %s+07:00 to %s+07:00
                - "3 giờ qua", "last 3 hours" → from %s+07:00 to %s+07:00
                - "6 giờ qua", "last 6 hours" → from %s+07:00 to %s+07:00
                - "12 giờ qua", "last 12 hours" → from %s+07:00 to %s+07:00
                - "24 giờ qua", "last 24 hours" → from %s+07:00 to %s+07:00
                
                TIME-OF-DAY RANGES (exact calculation):
                - "sáng nay", "this morning" → from %s+07:00 to %s+07:00 (if current time > 12:00), otherwise from %s+07:00 to %s+07:00
                - "chiều nay", "this afternoon" → from %s+07:00 to %s+07:00 (if current time > 18:00), otherwise from %s+07:00 to %s+07:00
                - "tối nay", "this evening" → from %s+07:00 to %s+07:00 (if current time > 22:00), otherwise from %s+07:00 to %s+07:00
                - "đêm nay", "tonight" → from %s+07:00 to %s+07:00
                
                YESTERDAY TIME RANGES:
                - "sáng hôm qua", "yesterday morning" → from %s+07:00 to %s+07:00
                - "chiều hôm qua", "yesterday afternoon" → from %s+07:00 to %s+07:00
                - "tối hôm qua", "yesterday evening" → from %s+07:00 to %s+07:00
                - "đêm qua", "last night" → from %s+07:00 to %s+07:00
                
                DAY-BASED RANGES:
                - "hôm nay", "today" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "hôm qua", "yesterday" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "2 ngày qua", "last 2 days" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "3 ngày qua", "last 3 days" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "4 ngày qua", "last 4 days" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "5 ngày qua", "last 5 days" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                - "1 tuần qua", "last week" → from %sT00:00:00+07:00 to %sT23:59:59+07:00
                
                RECENT/GENERAL TERMS:
                - "gần đây", "recent", "mới nhất" → from %s+07:00 to %s+07:00 (last 30 minutes)
                - "vừa rồi", "just now" → from %s+07:00 to %s+07:00 (last 5 minutes)
                
                IMPORTANT NOTES:
                - All timestamps use Vietnam timezone (+07:00)
                - Use 'gte' for start time and 'lte' for end time in Elasticsearch queries
                - For current time-based queries, calculate from exact current moment: %s
                - For "gần đây" without specific time, default to last 30 minutes
                """,
        currentDateTime, currentDate, currentTime,
        fiveMinutesAgo, currentDateTime,
        tenMinutesAgo, currentDateTime,
        fifteenMinutesAgo, currentDateTime,
        thirtyMinutesAgo, currentDateTime,
        fortyFiveMinutesAgo, currentDateTime,
        oneHourAgo, currentDateTime,
        twoHoursAgo, currentDateTime,
        threeHoursAgo, currentDateTime,
        sixHoursAgo, currentDateTime,
        twelveHoursAgo, currentDateTime,
        twentyFourHoursAgo, currentDateTime,
        todayMorning, currentDateTime, todayMorning, currentDateTime,
        todayAfternoon, currentDateTime, todayAfternoon, currentDateTime,
        todayEvening, currentDateTime, todayEvening, currentDateTime,
        todayNight, currentDateTime,
        yesterdayMorning, yesterdayMorning.substring(0,10) + " 11:59:59",
        yesterdayAfternoon, yesterdayAfternoon.substring(0,10) + " 17:59:59",
        yesterdayEvening, yesterdayEvening.substring(0,10) + " 21:59:59",
        yesterdayNight, currentDate + " 05:59:59",
        currentDate, currentDate,
        yesterday, yesterday,
        twoDaysAgo, currentDate,
        threeDaysAgo, currentDate,
        fourDaysAgo, currentDate,
        fiveDaysAgo, currentDate,
        oneWeekAgo, currentDate,
        thirtyMinutesAgo, currentDateTime,
        fiveMinutesAgo, currentDateTime,
        currentDateTime);
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
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            SchemaHint.getRoleNormalizationRules(),
            SchemaHint.getNetworkTrafficExamples(),
            SchemaHint.getIPSSecurityExamples(),
            SchemaHint.getAdminRoleExample(),
            SchemaHint.getGeographicExamples(),
            SchemaHint.getFirewallRuleExamples(),
            SchemaHint.getCountingExamples(),
            getFieldLog()
        )
    );

    List<String> schemaHints = SchemaHint.allSchemas();
    String schemaContext = String.join("\n\n", schemaHints);
    UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);


    UserMessage userMessage = new UserMessage(chatRequest.message());
    System.out.println(userMessage);
    System.out.println("----------------------------------------------------------");
    Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, userMessage));

    // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định và tuân thủ strict
    ChatOptions chatOptions = ChatOptions.builder()
        .temperature(0.0D)
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
              .temperature(0.0D)
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
                
                logData : %s
                query : %s
                
                Format your response as:
                [Your analysis and summary of the data based on current date %s]
                
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