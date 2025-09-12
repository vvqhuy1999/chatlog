package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AiServiceImpl implements AiService {
    // Lưu trữ thông tin mapping của Elasticsearch index để tránh gọi lại nhiều lần
    private static String fieldLog;

    // Client để giao tiếp với AI model (Spring AI)
    private final ChatClient chatClient;

    @Autowired
    private LogApiService logApiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        System.out.println(now);
        String dateContext = generateDateContext(now);
        System.out.println(dateContext);
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You are an Elasticsearch Query Generator. Your ONLY job is to convert user questions into Elasticsearch queries.
                
                %s
                
                CRITICAL RULES - FOLLOW EXACTLY:
                1. NEVER give direct answers or summaries
                2. NEVER say things like "Trong 5 ngày qua, có 50 kết nối..."
                3. ALWAYS generate an Elasticsearch query JSON.
                4. ALWAYS return the exact JSON format below
                5. If size is not define, Default size = 10.
                
                MANDATORY OUTPUT FORMAT (copy this structure exactly):
                {
                  "query": 1,
                  "body": "{"query":{"bool":{"must":[...]}},"size":100,"_source":[...]}"
                }
                
                EXAMPLE CORRECT RESPONSES:
                Question: "Show connections from IP 10.0.30.199 in last 3 days"
                Response: {"query":1,"body":"{"query":{"bool":{"must":[{"term":{"source.ip":"10.0.30.199"}},{"range":{"@timestamp":{"gte":"2025-09-08T00:00:00","lte":"2025-09-11T23:59:59"}}}]}},"size":100,"_source":["@timestamp","source.ip","destination.ip","source.user.name","event.action"]}"}
                
                Question: "Count total logs today using aggregation"
                Response: {"query":1,"body":"{"query":{"range":{"@timestamp":{"gte":"2025-09-11T00:00:00","lte":"2025-09-11T23:59:59"}}},"aggs":{"log_count":{"value_count":{"field":"@timestamp"}}},"size":0}"}
                
                Question: "Count successful and failed logins for user TrangNT today"
                Response: {"query":1,"body":"{"query":{"bool":{"must":[{"term":{"source.user.name":"TrangNT"}},{"range":{"@timestamp":{"gte":"2025-09-11T00:00:00","lte":"2025-09-11T23:59:59"}}}]}},"aggs":{"outcome_count":{"terms":{"field":"event.outcome"}}},"size":0,"_source":["@timestamp","source.user.name","event.action","event.outcome"]}"}
                
                Question: "Show logs in last 5 minutes"
                Response: {"query":1,"body":"{"query":{"range":{"@timestamp":{"gte":"2025-09-11T13:32:45","lte":"2025-09-11T13:37:45"}}},"size":50,"sort":[{"@timestamp":{"order":"desc"}}],"_source":["@timestamp","source.ip","destination.ip","source.user.name","event.action","event.outcome","message"]}"}
                
                CRITICAL STRUCTURE RULES:
                - ALL time range filters MUST be inside the "query" block
                - For aggregations, use "aggs" at the same level as "query"
                - NEVER put "range" outside the "query" block
                - Use "value_count" aggregation for counting total logs
                - Use "terms" aggregation for grouping by field values
                
                WRONG EXAMPLES (NEVER DO THIS):
                ❌ "Trong 5 ngày qua, có 50 kết nối được mở bởi IP 10.0.30.199"
                ❌ "Based on the logs, there were 30 connections..."
                ❌ Any text that is not the JSON format above
                ❌ Using "+07:00" in timestamps: "2025-09-11T14:30:45+07:00" (causes JSON parsing error)
                ❌ Putting "range" outside "query": {"query":{"match_all":{}},"range":{"@timestamp":{...}}} (WRONG STRUCTURE)
                ❌ Missing "source.user.name" in _source when searching for users
                
                Available Elasticsearch fields:
                %s
                
                Generate ONLY the JSON response. No explanations, no summaries, just the JSON.
                DateContext, Field list
                """, 
                dateContext,
                getFieldLog()));

        // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định
        ChatOptions chatOptions = ChatOptions.builder()
            .temperature(0.5D)
            .build();

        UserMessage userMessage = new UserMessage(chatRequest.message());
        System.out.println(userMessage);
        System.out.println("----------------------------------------------------------");
        // System.out.println(systemMessage);
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

            String flg = checkBodyFormat(requestBody);
            if (flg != null)
            {
                return flg;
            }

            content = getLogData(requestBody, chatRequest);


        }

        // Bước 3: Tóm tắt kết quả và trả lời người dùng
        return getAiResponse(sessionId,chatRequest,content, requestBody.getBody());
    }

    private String checkBodyFormat(RequestBody requestBody){
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

        return null;
    }

    private String getLogData(RequestBody requestBody, ChatRequest chatRequest)
    {
        // Bước 2: Luôn thực hiện tìm kiếm Elasticsearch (vì đã bắt buộc query = 1)
        // Cần tìm kiếm: gọi Elasticsearch và lấy dữ liệu log
        String content = "";
        content =  logApiService.search("logs-fortinet_fortigate.log-default*",
                requestBody.getBody());

        try {
            JsonNode jsonNode = objectMapper.readTree(content);
            int totalHits = jsonNode.path("hits").path("total").path("value").asInt();

            if (totalHits == 0) {
                String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
                String prevQuery = requestBody.getBody();
                String userMess = chatRequest.message();

                String systemMsg = """
                    The user request may not be fully accurate, or the previous query may not be correct.
                    Please rely on the correct fields to generate a new, valid ElasticSearch query 
                    that best matches the user request.
                    Correct fields: %s
                    """.formatted(allFields);

                Prompt comparePrompt = new Prompt(
                        new SystemMessage(systemMsg),
                        new UserMessage("User request: " + userMess + " | Previous query: " + prevQuery)
                );

                ChatOptions chatOptions = ChatOptions.builder()
                        .temperature(0D)
                        .build();

                requestBody = chatClient.prompt(comparePrompt)
                        .options(chatOptions)
                        .call()
                        .entity(new ParameterizedTypeReference<>() {});

                content = logApiService.search("logs-fortinet_fortigate.log-default*",
                        requestBody.getBody());
            }
        }
        catch (Exception e)
        {
            System.out.println("[AiServiceImpl] - getLogData: " +e.getMessage());
        }
        return content;
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
        System.out.println("[AiServiceImpl] Content: " + content);
        // Tạo system message hướng dẫn AI cách phản hồi
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You are HPT.AI
                You should respond in a formal voice.
                logData, query
                """,content,query));

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
