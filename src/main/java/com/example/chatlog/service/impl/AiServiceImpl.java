package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.SchemaHint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
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
//        System.out.println(now);
        String dateContext = generateDateContext(now);
//        System.out.println(dateContext);
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You will act as an expert in Elasticsearch and Elastic Stack search; read the question and write a query that precisely captures the question’s intent.
                
                %s
                
                CRITICAL RULES - FOLLOW EXACTLY:
                1. NEVER give direct answers or summaries
                2. NEVER say things like "Trong 5 ngày qua, có 50 kết nối..."
                3. ALWAYS generate an Elasticsearch query JSON.
                4. ALWAYS return the exact JSON format below
                5. If size is not define, Default size = 10.
                6. Try to use the SchemaHint to get data.
                7. ALWAYS set query = 1 in your response to enable search.
                8. ALWAYS use '+07:00' timezone format in timestamps (Vietnam timezone).
                9. ALWAYS return a single-line JSON response without line breaks or string concatenation.
                10. The current date is %s. Use the REAL-TIME CONTEXT provided above for all time calculations.
                11. NEVER mention dates in the future or incorrect current time in your reasoning.
                
                TIMESTAMP FORMAT RULES:
                - CORRECT: "2025-09-14T10:55:55.000+07:00"
                - INCORRECT: "2025-09-14T10:55:55.000Z"
                - Use Vietnam timezone (+07:00) to match the data in Elasticsearch
                
                JSON FORMAT RULES:
                - NEVER use line breaks in the JSON response
                - NEVER use string concatenation with '+' operator
                - Return the entire JSON as a single continuous string
                - When using +07:00 in timestamps, ensure it's properly escaped in JSON strings
                
                FIELD MAPPING RULES:
                - Use exact field names from mapping, don't add .keyword unless confirmed
                - For terms aggregation, check if field supports aggregation
                - If unsure about field type, use simple field name without .keyword
                - Example: use "source.user.name" not "source.user.name.keyword"
                
                IMPORTANT FIELD MAPPINGS:
                - "tổ chức", "organization", "công ty" → use "destination.as.organization.name"
                - "người dùng", "user" → use "source.user.name"
                - "địa chỉ IP", "IP address" → use "source.ip" or "destination.ip"
                - "hành động", "action" → use "event.action"
                - Always use "must" as array: [{"term": {...}}, {"range": {...}}]
                
                %s
                
                IMPORTANT: Do NOT add filters like "must_not", "local", "external" unless explicitly mentioned.
                "bên ngoài" (external) does NOT require must_not filters - all destinations are external by default.
                
                
                CRITICAL STRUCTURE RULES:
                - ALL time range filters MUST be inside the "query" block
                - For aggregations, use "aggs" at the same level as "query"
                - NEVER put "range" outside the "query" block
                - Use "value_count" aggregation for counting total logs
                - Use "terms" aggregation for grouping by field values
                - NEVER use "must_not" unless explicitly asked to exclude something
                - NEVER add filters for "local", "external", "internal" - stick to what's asked
                
                COUNTING QUESTIONS RULES:
                - Questions with "tổng", "count", "bao nhiêu", "số lượng" ALWAYS need "aggs" with "value_count"
                - ALWAYS set "size": 0 for counting queries
                - Example counting keywords: "tổng có bao nhiêu", "có bao nhiêu", "đếm", "count"
                
                REQUIRED JSON FORMAT:
                {
                  "query": { ... elasticsearch query ... },
                  "size": 10,
                  "_source": ["@timestamp", "source.ip", ...],
                  "sort": [{"@timestamp": {"order": "desc"}}]
                }
                
                For aggregations, add "aggs" at the same level as "query":
                {
                  "query": { ... },
                  "aggs": { ... },
                  "size": 0
                }
                
                RESPONSE FORMAT:
                You must return a RequestBody object with these fields:
                - body: The JSON query string for Elasticsearch
                - query: MUST be set to 1 to enable search functionality
                
                EXAMPLE CORRECT RESPONSES:
                Question: "Get last 10 logs from yesterday"
                Response: {"body":"{\"query\":{\"range\":{\"@timestamp\":{\"gte\":\"2025-09-14T00:00:00.000+07:00\",\"lte\":\"2025-09-14T23:59:59.999+07:00\"}}},\"size\":10,\"sort\":[{\"@timestamp\":{\"order\":\"desc\"}}]}","query":1}
                
                Question: "Count total logs today"
                Response: {"body":"{\"query\":{\"range\":{\"@timestamp\":{\"gte\":\"2025-09-15T00:00:00.000+07:00\",\"lte\":\"2025-09-15T23:59:59.999+07:00\"}}},\"aggs\":{\"log_count\":{\"value_count\":{\"field\":\"@timestamp\"}}},\"size\":0}","query":1}
                
                Question: "danh sách tổ chức đích mà NhuongNT truy cập"
                Response: {"body":"{\"query\":{\"bool\":{\"must\":[{\"term\":{\"source.user.name\":\"NhuongNT\"}},{\"range\":{\"@timestamp\":{\"gte\":\"2025-09-15T00:00:00.000+07:00\",\"lte\":\"2025-09-15T23:59:59.999+07:00\"}}}]}},\"aggs\":{\"organizations\":{\"terms\":{\"field\":\"destination.as.organization.name\",\"size\":10}}},\"size\":0}","query":1}
                
                Question: "tổ chức bên ngoài mà user ABC truy cập"
                Response: {"body":"{\"query\":{\"bool\":{\"must\":[{\"term\":{\"source.user.name\":\"ABC\"}},{\"range\":{\"@timestamp\":{\"gte\":\"2025-09-15T00:00:00.000+07:00\",\"lte\":\"2025-09-15T23:59:59.999+07:00\"}}}]}},\"aggs\":{\"external_orgs\":{\"terms\":{\"field\":\"destination.as.organization.name\",\"size\":10}}},\"size\":0}","query":1}
                
                Question: "tổng có bao nhiêu log ghi nhận từ người dùng TuNM trong ngày hôm nay"
                Response: {"body":"{\"query\":{\"bool\":{\"must\":[{\"term\":{\"source.user.name\":\"TuNM\"}},{\"range\":{\"@timestamp\":{\"gte\":\"2025-09-15T00:00:00.000+07:00\",\"lte\":\"2025-09-15T23:59:59.999+07:00\"}}}]}},\"aggs\":{\"log_count\":{\"value_count\":{\"field\":\"@timestamp\"}}},\"size\":0}","query":1}
                
                %s
                
                Available Elasticsearch fields:
                %s
                
                Generate ONLY the JSON response. No explanations, no summaries, just the JSON.
                """,
            dateContext,
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            SchemaHint.getRoleNormalizationRules(),
            SchemaHint.getAdminRoleExample(),
            getFieldLog()));


        List<String> schemaHints = SchemaHint.allSchemas();
        String schemaContext = String.join("\n\n", schemaHints);
        UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);


        UserMessage userMessage = new UserMessage(chatRequest.message());
        System.out.println(userMessage);
        System.out.println("----------------------------------------------------------");
        Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, userMessage));

        // Cấu hình ChatClient với temperature = 0 để có kết quả ổn định và tuân thủ strict
        ChatOptions chatOptions = ChatOptions.builder()
            .temperature(0.6D)
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

            // Thử lấy raw response và fix manually
            String rawResponse = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .content();

            System.out.println("[AiServiceImpl] Raw AI response: " + rawResponse);

            // Fix JSON format issues
            String fixedResponse = fixAiJsonResponse(rawResponse);
            System.out.println("[AiServiceImpl] Fixed AI response: " + fixedResponse);

            try {
                ObjectMapper mapper = new ObjectMapper();
                requestBody = mapper.readValue(fixedResponse, RequestBody.class);
                System.out.println("[AiServiceImpl] Successfully parsed fixed response");
            } catch (Exception ex) {
                System.out.println("[AiServiceImpl] ERROR: Still failed to parse after fixing: " + ex.getMessage());
                return "❌ AI model trả về format không hợp lệ và không thể sửa được: " + ex.getMessage();
            }
        }

        // Đảm bảo query luôn là 1 (bắt buộc tìm kiếm)
        if (requestBody.getQuery() != 1) {
            System.out.println("[AiServiceImpl] Setting query=1 (was " + requestBody.getQuery() + ")");
            requestBody.setQuery(1);
        }

        System.out.println("THong tin quey: "+requestBody.getQuery());
        System.out.println("[AiServiceImpl] Generated query body: " + requestBody.getBody());
        System.out.println("[AiServiceImpl] Using current date context: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        String fixedQuery = requestBody.getBody(); // Default value

        // Validation: Kiểm tra xem body có phải là JSON query hay không
        if (requestBody.getBody() != null) {

            String flg = checkBodyFormat(requestBody);
            System.out.println("flg: " + flg);
            if (flg != null)
            {
                return flg;
            }


            String[] result = getLogData(requestBody, chatRequest);
            content = result[0];
            fixedQuery = result[1];

            System.out.println("content: " + content);


        }



        // Bước 3: Tóm tắt kết quả và trả lời người dùng
        return getAiResponse(sessionId,chatRequest,content, fixedQuery);
    }

    private String checkBodyFormat(RequestBody requestBody){
        String originalBody = requestBody.getBody().trim();
        String body = originalBody;

        // Fix JSON formatting by balancing braces
        body = fixJsonBraces(body);

        // Log if JSON was fixed
        if (!body.equals(originalBody)) {
            System.out.println("[AiServiceImpl] JSON was automatically fixed:");
            System.out.println("[AiServiceImpl] Original: " + originalBody);
            System.out.println("[AiServiceImpl] Fixed:    " + body);

            // When JSON is fixed, we should regenerate the query field to ensure consistency
            // For now, we'll keep the original query but log a warning
            System.out.println("[AiServiceImpl] WARNING: JSON was fixed but query field may be inconsistent");
            System.out.println("[AiServiceImpl] Current query field: " + requestBody.getQuery());

            // Regenerate RequestBody to ensure consistency between body and query fields
            requestBody = regenerateRequestBodyWithFixedJson(body, requestBody.getQuery());
        }

        try {
            JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requestBody.getBody());

            // Validate that it's a proper Elasticsearch query
            if (!jsonNode.has("query") && !jsonNode.has("aggs")) {
                System.out.println("[AiServiceImpl] ERROR: Missing 'query' or 'aggs' field!");
                return "❌ AI model trả về query không hợp lệ. Cần có 'query' hoặc 'aggs' field.";
            }

        } catch (Exception e) {
            System.out.println("[AiServiceImpl] ERROR: Invalid JSON format even after auto-fix!");
            System.out.println("[AiServiceImpl] Expected: JSON object with 'query' field");
            System.out.println("[AiServiceImpl] Received: " + requestBody.getBody());
            System.out.println("[AiServiceImpl] Error details: " + e.getMessage());
            return "❌ AI model trả về format không đúng. Đã cố gắng sửa tự động nhưng vẫn không hợp lệ. Cần JSON query, nhận được: " + requestBody.getBody();
        }

        return null;
    }

    /**
     * Fix JSON by balancing opening and closing braces
     * Remove extra closing braces that don't have matching opening braces
     */
    private String fixJsonBraces(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        boolean escaped = false;

        // Count braces outside of string literals
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    openBraces++;
                } else if (c == '}') {
                    closeBraces++;
                }
            }
        }

        // If we have extra closing braces, remove them from the end
        if (closeBraces > openBraces) {
            int extraBraces = closeBraces - openBraces;
            String result = json;

            // Remove extra closing braces from the end
            for (int i = 0; i < extraBraces; i++) {
                int lastBraceIndex = result.lastIndexOf('}');
                if (lastBraceIndex > 0) {
                    result = result.substring(0, lastBraceIndex).trim();
                }
            }

            System.out.println("[AiServiceImpl] Fixed JSON: Removed " + extraBraces + " extra closing braces");
            System.out.println("[AiServiceImpl] Fixed JSON result: " + result);
            return result;
        }

        return json;
    }

    /**
     * Regenerate RequestBody with fixed JSON to ensure consistency
     * This creates a new RequestBody with the fixed JSON and preserves the query field
     */
    private RequestBody regenerateRequestBodyWithFixedJson(String fixedJson, int originalQuery) {
        System.out.println("[AiServiceImpl] Regenerating RequestBody with fixed JSON");
        // Đảm bảo query luôn là 1, bất kể giá trị originalQuery
        return new RequestBody(fixedJson, 1);
    }

    /**
     * Fix AI JSON response by removing line breaks and string concatenation
     * @param rawResponse Raw response from AI
     * @return Fixed JSON string
     */
    private String fixAiJsonResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return rawResponse;
        }

        String fixed = rawResponse;

        // Remove line breaks and extra whitespace
        fixed = fixed.replaceAll("\\n", "").replaceAll("\\r", "").trim();

        // Fix string concatenation with + operator
        // Pattern: "text1" + "text2" -> "text1text2"
        fixed = fixed.replaceAll("\"\\s*\\+\\s*\"", "");

        // Fix cases where there are spaces around +
        fixed = fixed.replaceAll("\"\\s*\\+\\s*\\n\\s*\"", "");

        // Remove extra spaces
        fixed = fixed.replaceAll("\\s+", " ");

        System.out.println("[AiServiceImpl] Fixed JSON concatenation issues");
        return fixed;
    }

    /**
     * Xử lý một match clause và chuyển đổi thành term nếu cần
     * @param clause JsonNode chứa match clause
     * @param mapper ObjectMapper để tạo nodes
     * @param targetArray ArrayNode để thêm kết quả
     * @param index Index để set trong array
     * @return true nếu có thay đổi
     */
    private boolean processMatchClause(JsonNode clause, ObjectMapper mapper, ArrayNode targetArray, int index) {
        if (!clause.has("match")) {
            return false;
        }

        JsonNode matchNode = clause.get("match");
        @SuppressWarnings("deprecation")
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = matchNode.fields();

        boolean modified = false;
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldIterator.next();
            String fieldName = field.getKey();

            if (fieldName.equals("event.action") ||
                fieldName.equals("event.outcome") ||
                fieldName.equals("source.user.name") ||
                fieldName.equals("source.user.roles")) {

                // Chuẩn hóa roles nếu là trường source.user.roles
                JsonNode fieldValue = field.getValue();
                if (fieldName.equals("source.user.roles") && fieldValue.isTextual()) {
                    String originalRole = fieldValue.asText();
                    String normalizedRole = SchemaHint.normalizeRole(originalRole);
                    if (!originalRole.equals(normalizedRole)) {
                        System.out.println("[AiServiceImpl] Normalized role: " + originalRole + " -> " + normalizedRole);
                        fieldValue = mapper.valueToTree(normalizedRole);
                    }
                }

                // Tạo term query mới
                ObjectNode termQuery = mapper.createObjectNode();
                ObjectNode termField = mapper.createObjectNode();
                termField.set(fieldName, fieldValue);
                termQuery.set("term", termField);

                // Thêm vào array tại vị trí index
                if (targetArray.size() <= index) {
                    targetArray.add(termQuery);
                } else {
                    targetArray.set(index, termQuery);
                }
                modified = true;
            }
        }

        // Nếu không có thay đổi, thêm clause gốc vào array
        if (!modified && targetArray.size() <= index) {
            targetArray.add(clause);
        }

        return modified;
    }

    /**
     * Xử lý một term clause và chuẩn hóa roles nếu cần
     * @param clause JsonNode chứa term clause
     * @param mapper ObjectMapper để tạo nodes
     * @param targetArray ArrayNode để thêm kết quả
     * @param index Index để set trong array
     * @return true nếu có thay đổi
     */
    private boolean processTermClause(JsonNode clause, ObjectMapper mapper, ArrayNode targetArray, int index) {
        if (!clause.has("term")) {
            return false;
        }

        JsonNode termNode = clause.get("term");
        @SuppressWarnings("deprecation")
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = termNode.fields();

        boolean modified = false;
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldIterator.next();
            String fieldName = field.getKey();

            if (fieldName.equals("source.user.roles")) {
                // Chuẩn hóa roles
                JsonNode fieldValue = field.getValue();
                if (fieldValue.isTextual()) {
                    String originalRole = fieldValue.asText();
                    String normalizedRole = SchemaHint.normalizeRole(originalRole);
                    if (!originalRole.equals(normalizedRole)) {
                        System.out.println("[AiServiceImpl] Normalized role in term query: " + originalRole + " -> " + normalizedRole);
                        
                        // Tạo term query mới với role đã chuẩn hóa
                        ObjectNode termQuery = mapper.createObjectNode();
                        ObjectNode termField = mapper.createObjectNode();
                        termField.put(fieldName, normalizedRole);
                        termQuery.set("term", termField);

                        // Thay thế clause cũ
                        targetArray.set(index, termQuery);
                        modified = true;
                    }
                }
            }
        }

        return modified;
    }

    /**
     * Sửa các lỗi mapping phổ biến trong Elasticsearch query
     * @param query JSON query gốc
     * @return JSON query đã sửa
     */
    private String fixElasticsearchQuery(String query) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(query);

            // Tạo một bản sao để sửa
            JsonNode fixedRoot = rootNode.deepCopy();
            final boolean[] modified = {false};  // Sử dụng array để có thể thay đổi từ lambda

            // Sửa lỗi phổ biến #1: Thêm .keyword cho aggregation fields
            if (fixedRoot.has("aggs")) {
                JsonNode aggsNode = fixedRoot.get("aggs");
                // Duyệt qua các aggregation
                @SuppressWarnings("deprecation")
                Iterator<Map.Entry<String, JsonNode>> aggIterator = aggsNode.fields();
                while (aggIterator.hasNext()) {
                    Map.Entry<String, JsonNode> entry = aggIterator.next();
                    JsonNode agg = entry.getValue();

                    // Kiểm tra nếu đây là terms aggregation
                    if (agg.has("terms") && agg.get("terms").has("field")) {
                        String fieldName = agg.get("terms").get("field").asText();

                        // DISABLED: Tự động thêm .keyword có thể sai mapping thực tế
                        // Để AI tự tạo query đúng thay vì fix
                        /*
                        if (fieldName.equals("source.user.name") && !fieldName.endsWith(".keyword")) {
                            ((ObjectNode)agg.get("terms"))
                                .put("field", fieldName + ".keyword");
                            modified[0] = true;
                        }
                        */
                    }
                }
            }

            // Sửa lỗi phổ biến #2: Chuyển match thành term cho keyword fields
            if (fixedRoot.has("query") && fixedRoot.get("query").has("bool") &&
                fixedRoot.get("query").get("bool").has("must")) {

                JsonNode mustNode = fixedRoot.get("query").get("bool").get("must");

                // Xử lý trường hợp must là array
                if (mustNode.isArray()) {
                    for (int j = 0; j < mustNode.size(); j++) {
                        final int index = j;  // Tạo biến final cho lambda
                        JsonNode clause = mustNode.get(index);

                        if (processMatchClause(clause, mapper, (ArrayNode)mustNode, index)) {
                            modified[0] = true;
                        }
                        if (processTermClause(clause, mapper, (ArrayNode)mustNode, index)) {
                            modified[0] = true;
                        }
                    }
                }
                // Xử lý trường hợp must là object đơn
                else if (mustNode.isObject()) {
                    ArrayNode mustArray = mapper.createArrayNode();
                    boolean hasChanges = false;
                    
                    if (mustNode.has("match")) {
                        if (processMatchClause(mustNode, mapper, mustArray, 0)) {
                            hasChanges = true;
                        } else {
                            mustArray.add(mustNode);
                        }
                    } else if (mustNode.has("term")) {
                        if (processTermClause(mustNode, mapper, mustArray, 0)) {
                            hasChanges = true;
                        } else {
                            mustArray.add(mustNode);
                        }
                    } else {
                        mustArray.add(mustNode);
                    }
                    
                    // Luôn chuyển thành array để chuẩn hóa
                    ((ObjectNode)fixedRoot.get("query").get("bool")).set("must", mustArray);
                    if (hasChanges) {
                        modified[0] = true;
                    }
                }
            }

            // Trả về query đã sửa nếu có thay đổi
            if (modified[0]) {
                String fixedQuery = mapper.writeValueAsString(fixedRoot);
                System.out.println("[AiServiceImpl] Fixed Elasticsearch query mapping issues:");
                System.out.println("[AiServiceImpl] Original: " + query);
                System.out.println("[AiServiceImpl] Fixed: " + fixedQuery);
                return fixedQuery;
            }

            return query;
        } catch (Exception e) {
            System.out.println("[AiServiceImpl] Error in fixElasticsearchQuery: " + e.getMessage());
            return query;
        }
    }

    private String[] getLogData(RequestBody requestBody, ChatRequest chatRequest)
    {
        // Bước 2: Luôn thực hiện tìm kiếm Elasticsearch (vì đã bắt buộc query = 1)
        // Cần tìm kiếm: gọi Elasticsearch và lấy dữ liệu log
        String content = "";
        String fixedQuery = "";
        try{
            // Sửa query trước khi gửi đến Elasticsearch
            fixedQuery = fixElasticsearchQuery(requestBody.getBody());
            System.out.println("[AiServiceImpl] Sending query to Elasticsearch: " + fixedQuery);

            content = logApiService.search("logs-fortinet_fortigate.log-default*", fixedQuery);
            System.out.println("[AiServiceImpl] Elasticsearch response received successfully");
        }catch (Exception e){
            content="";
            System.out.println("[AiServiceImpl] ERROR: Log API returned an error! " + e.getMessage());

            // Thử với một query đơn giản hơn để kiểm tra kết nối
            try {
                String simpleQuery = """
                {
                  "size": 1,
                  "query": {
                    "match_all": {}
                  },
                  "sort": [{"@timestamp": {"order": "desc"}}]
                }
                """;
                System.out.println("[AiServiceImpl] Trying with a simple query: " + simpleQuery);
                String simpleResult = logApiService.search("logs-fortinet_fortigate.log-default*", simpleQuery);

                if (simpleResult != null && !simpleResult.isEmpty() && !simpleResult.contains("error")) {
                    System.out.println("[AiServiceImpl] Simple query succeeded, there's likely a mapping issue with the original query");
                    // Lấy mapping từ Elasticsearch để debug
                    String mappingInfo = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
                    System.out.println("[AiServiceImpl] Available fields: " + mappingInfo);
                }
            } catch (Exception ex) {
                System.out.println("[AiServiceImpl] Simple query also failed: " + ex.getMessage());
            }
        }



        try {
            JsonNode jsonNode = objectMapper.readTree(content);
            int totalHits = jsonNode.path("hits").path("total").path("value").asInt();

            if (totalHits == 0) {
                String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
                String prevQuery = requestBody.getBody();
                String userMess = chatRequest.message();

                String systemMsg = String.format("""
                    You are an Elasticsearch Query Generator. Re-generate the query to match the user request better.
                    
                    CRITICAL RULES - FOLLOW EXACTLY:
                    1. ALWAYS return RequestBody with body (JSON string) and query (set to 1)
                    2. ALWAYS use '+07:00' timezone format in timestamps (Vietnam timezone)
                    3. ALWAYS return single-line JSON without line breaks or string concatenation
                    4. NEVER use line breaks in JSON response
                    5. NEVER use string concatenation with '+' operator
                    6. Return entire JSON as single continuous string
                    
                    TIMESTAMP FORMAT:
                    - CORRECT: "2025-09-14T11:41:04.000+07:00"
                    - INCORRECT: "2025-09-14T11:41:04.000Z"
                    
                    Available fields: %s
                    
                    Return ONLY the RequestBody JSON. No explanations.
                    Example: {"body":"{\"query\":{\"match_all\":{}},\"size\":10}","query":1}
                    """, allFields);

                Prompt comparePrompt = new Prompt(
                    new SystemMessage(systemMsg),
                    new UserMessage("User request: " + userMess + " | Previous query: " + prevQuery)
                );

                ChatOptions chatOptions = ChatOptions.builder()
                    .temperature(0D)
                    .build();

                try {
                    requestBody = chatClient.prompt(comparePrompt)
                        .options(chatOptions)
                        .call()
                        .entity(new ParameterizedTypeReference<>() {});
                } catch (Exception e) {
                    System.out.println("[AiServiceImpl] ERROR: Failed to parse regenerated AI response: " + e.getMessage());

                    // Thử lấy raw response và fix manually
                    String rawResponse = chatClient.prompt(comparePrompt)
                        .options(chatOptions)
                        .call()
                        .content();

                    System.out.println("[AiServiceImpl] Raw regenerated AI response: " + rawResponse);

                    // Fix JSON format issues
                    String fixedResponse = fixAiJsonResponse(rawResponse);
                    System.out.println("[AiServiceImpl] Fixed regenerated AI response: " + fixedResponse);

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        requestBody = mapper.readValue(fixedResponse, RequestBody.class);
                        System.out.println("[AiServiceImpl] Successfully parsed fixed regenerated response");
                    } catch (Exception ex) {
                        System.out.println("[AiServiceImpl] ERROR: Failed to parse regenerated response even after fixing: " + ex.getMessage());
                        content = "Failure to regenerate query";
                        return new String[]{content, fixedQuery != null ? fixedQuery : requestBody.getBody()};
                    }
                }

                // Đảm bảo query luôn là 1 cho query được tạo lại
                if (requestBody.getQuery() != 1) {
                    System.out.println("[AiServiceImpl] Setting query=1 for regenerated query (was " + requestBody.getQuery() + ")");
                    requestBody.setQuery(1);
                }

                System.out.println("[AiServiceImpl] Generated query body2: " + requestBody.getBody());

                // Sửa query trước khi gửi đến Elasticsearch
                fixedQuery = fixElasticsearchQuery(requestBody.getBody());
                System.out.println("[AiServiceImpl] Sending regenerated query to Elasticsearch: " + fixedQuery);
                content = logApiService.search("logs-fortinet_fortigate.log-default*", fixedQuery);
            }
        }
        catch (Exception e)
        {
            content = "Failure to request data";
            System.out.println("[AiServiceImpl] - getLogData: "+content+ " " +e.getMessage());
            // Nếu có lỗi và fixedQuery chưa được set, sử dụng query gốc
            if (fixedQuery == null || fixedQuery.isEmpty()) {
                fixedQuery = requestBody.getBody();
            }
        }

        // Trả về mảng: [0] = content, [1] = fixedQuery
        return new String[]{content, fixedQuery};
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
            .options(ChatOptions.builder().temperature(0.1D).build())
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