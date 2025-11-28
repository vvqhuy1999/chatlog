package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.DataExample;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.SchemaHint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Service xử lý chuyển đổi query của người dùng sang Elasticsearch DSL
 * Bao gồm: tạo query, validate, sửa lỗi, và thực hiện tìm kiếm
 */
@Service
public class AiQueryService {

    @Autowired
    private LogApiService logApiService;

    // ✅ Inject KnowledgeBaseIndexingService thay vì load lại file
    @Autowired
    private KnowledgeBaseIndexingService knowledgeBaseIndexingService;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiQueryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get the example library from KnowledgeBaseIndexingService
     */
    public List<DataExample> getExampleLibrary() {
        return knowledgeBaseIndexingService.getExampleLibrary();
    }



    /**
     * Tạo chuỗi thông tin ngày tháng cho system message với các biểu thức thời gian tương đối của Elasticsearch
     */
    private String generateDateContext() {
        ZonedDateTime vnTimeNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ZonedDateTime utcTimeNow = vnTimeNow.withZoneSameInstant(ZoneId.of("UTC"));
        
        // "Hôm nay" = từ 00:00:00 VN hôm nay đến thời điểm hiện tại
        ZonedDateTime todayStartVN = vnTimeNow.toLocalDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"));
        ZonedDateTime todayStartUTC = todayStartVN.withZoneSameInstant(ZoneId.of("UTC"));

        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        DateTimeFormatter vnFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+07:00'");

        System.out.println("[generateDateContext] 📅 REALTIME Vietnam: " + vnTimeNow.format(displayFormat));
        System.out.println("[generateDateContext] 🌍 REALTIME UTC: " + utcTimeNow.format(displayFormat));
        System.out.println("[generateDateContext] 🌅 Today start VN (00:00): " + todayStartVN.format(displayFormat));
        System.out.println("[generateDateContext] 🌅 Today start UTC: " + todayStartUTC.format(displayFormat));

        String dateContext = String.format("""
                ═══════════════════════════════════════════════════════════════
                🚨 MANDATORY TIMEZONE RULE (PRIORITY #0)
                ═══════════════════════════════════════════════════════════════
                
                User is in VIETNAM (UTC+7). All Elasticsearch logs are in UTC.
                
                🕐 REALTIME NOW:
                - Vietnam time NOW: %s (UTC+7)
                - UTC time NOW: %s
                - Today's date in Vietnam: %s
                
                ⚠️ CRITICAL: When user mentions "hôm nay" (today), it means from 00:00 VN today until NOW:
                
                📐 TIMEZONE CONVERSION:
                - Vietnam: {"gte": "%s", "lt": "%s"}
                - UTC:     {"gte": "%s", "lt": "%s"}
                
                ✅ CORRECT UTC TIMESTAMPS FOR "HÔM NAY" (use this):
                {"gte": "%s", "lt": "%s"}
                
                📌 COPY-PASTE READY:
                "range": {"@timestamp": {"gte": "%s", "lt": "%s"}}
                
                ═══════════════════════════════════════════════════════════════
                
                OTHER TIME EXPRESSIONS:
                - "1 giờ qua" → {"gte": "now-1h"}
                - "6 giờ qua" → {"gte": "now-6h"}  
                - "7 ngày qua" → {"gte": "now-7d"}
                """,
            vnTimeNow.format(displayFormat),
            utcTimeNow.format(displayFormat),
            vnTimeNow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            // Vietnam timestamps
            todayStartVN.format(vnFormat),
            vnTimeNow.format(vnFormat),
            // UTC timestamps
            todayStartUTC.format(isoFormat),
            utcTimeNow.format(isoFormat),
            // Correct UTC for "hôm nay"
            todayStartUTC.format(isoFormat),
            utcTimeNow.format(isoFormat),
            // Copy-paste ready
            todayStartUTC.format(isoFormat),
            utcTimeNow.format(isoFormat)
        );

        return dateContext;
    }

    /**
     * Thực hiện tìm kiếm Elasticsearch với retry logic
     */
    public String[] getLogData(RequestBody requestBody, ChatRequest chatRequest) {
        String query = requestBody.getBody();

        // First try to fix common query structure issues
        String fixedQuery = fixQueryStructure(query);
        if (!fixedQuery.equals(query)) {
            System.out.println("[AiQueryService] 🔧 Query structure was automatically fixed");
            query = fixedQuery;
        }

        // Validate query syntax before sending to Elasticsearch
        String validationError = validateQuerySyntax(query);
        if (validationError != null) {
            System.out.println("[AiQueryService] Query validation failed: " + validationError);
            return new String[]{
                "❌ **Query Validation Error**\n\n" +
                    "Query có cú pháp không hợp lệ trước khi gửi đến Elasticsearch.\n\n" +
                    "**Lỗi validation:** " + validationError + "\n\n" +
                    "💡 **Gợi ý:** Vui lòng thử câu hỏi khác hoặc kiểm tra lại cấu trúc query.",
                query
            };
        }

        try {
            System.out.println("[AiQueryService] Sending query to Elasticsearch: " + query);
            String content = logApiService.search("logs-*", query);
            // System.out.println("[AiQueryService] Elasticsearch response received successfully");

            // 🔍 DEBUG: Kiểm tra response có phải empty hay error không
            if (content == null || content.trim().isEmpty()) {
                System.out.println("[AiQueryService] ⚠️ WARNING: Elasticsearch returned EMPTY response!");
                return new String[]{
                    "⚠️ Elasticsearch trả về response rỗng (empty)\n\nKiểm tra lại query hoặc dữ liệu trong Elasticsearch.",
                    query
                };
            }

            // Kiểm tra xem response có chứa error không
            if (content.contains("\"error\"") || content.contains("error_type")) {
                System.out.println("[AiQueryService] ⚠️ WARNING: Elasticsearch returned ERROR in response!");
                System.out.println("[AiQueryService] Response: " + content.substring(0, Math.min(200, content.length())));
                return new String[]{
                    "❌ Elasticsearch trả về lỗi:\n\n" + content,
                    query
                };
            }

            // ✅ Kiểm tra xem có hits hoặc aggregations không
            // CHỈ check hits empty NẾU KHÔNG CÓ aggregations (vì size:0 query sẽ có aggs thay vì hits)
            boolean hasAggregations = content.contains("\"aggregations\"") || content.contains("\"aggs\"");
            boolean hitsEmpty = content.contains("\"hits\":[]") || content.contains("\"hits\": []");

            // Log trạng thái để debug
            System.out.println("[AiQueryService] hasAggregations=" + hasAggregations + ", hitsEmpty=" + hitsEmpty);

            if (hitsEmpty && !hasAggregations) {
                System.out.println("[AiQueryService] ℹ️ INFO: Elasticsearch returned 0 results (no hits and no aggregations)");
                return new String[]{
                    "ℹ️ Không tìm thấy kết quả (0 hits) từ Elasticsearch.",
                    query
                };
            }


            System.out.println("[AiQueryService] ✅ Valid response received with data");
            return new String[]{content, query};

        } catch (Exception e) {
            System.out.println("[AiQueryService] ERROR: Log API returned an error! " + e.getMessage());

            // Parse error details từ Elasticsearch
            String errorDetails = extractElasticsearchError(e.getMessage());
            System.out.println("[AiQueryService] Parsed error details: " + errorDetails);

            // Nếu là lỗi 400 Bad Request, thử sửa query bằng AI và retry một lần
            if (e.getMessage().contains("400") || e.getMessage().contains("Bad Request") ||
                e.getMessage().contains("parsing_exception") || e.getMessage().contains("illegal_argument_exception")) {

                System.out.println("[AiQueryService] 🔄 Đang thử sửa query với AI và retry...");

                try {
                    // Lấy field mapping và tạo comparison prompt với error details
                    String allFields = logApiService.getAllField("logs-*");
                    String prevQuery = requestBody.getBody();
                    String userMess = chatRequest.message();

                    // Cải thiện prompt với error details cụ thể
                    String systemPrompt =
                        com.example.chatlog.utils.QueryPromptTemplate.getComparisonPrompt(
                            allFields, prevQuery, userMess, generateDateContext()
                        )
                            + "\n\nROLE: You are an expert Elasticsearch DSL fixer.\n"
                            + "Context:\n"
                            + "- allFields: the complete list of valid field names (with types if available). Use only these.\n"
                            + "- prevQuery: the failing query to fix without changing the user's intent.\n"
                            + "- userMess: user's intent. Preserve semantics.\n"
                            + "- dateContext: current time context if needed.\n\n"
                            + "Task:\n"
                            + "- Fix the specific issue in errorDetails.\n"
                            + "- Keep the user's intent unchanged.\n"
                            + "- Use only fields present in allFields; replace or remove invalid fields appropriately.\n\n"
                            + "Output requirements:\n"
                            + "- Return ONLY a single valid Elasticsearch JSON query. No explanations, no extra text.\n"
                            + "- Ensure valid JSON syntax.\n\n"
                            + "Best practices and constraints:\n"
                            + "1) Do NOT place \"aggs\" inside \"query\". Use proper root-level aggs (or nested aggs correctly when needed).\n"
                            + "2) Validate bool structure: must/should/filter/must_not used correctly.\n"
                            + "3) Use operators matching field types (term/terms vs match; range for numeric/date, keyword vs text).\n"
                            + "4) Ensure brackets, quotes, and commas are properly balanced.\n"
                            + "5) If date filters are present, honor date formats and time zones. Use dateContext as needed.\n"
                            + "6) Preserve size/sort/from if valid; otherwise fix or remove with minimal change.\n"
                            + "7) Mentally verify the query passes syntax and mapping checks before returning.\n";

                    String userPrompt =
                        "URGENT: Fix this Elasticsearch query and ensure correct syntax.\n\n"
                            + "errorDetails: " + errorDetails + "\n"
                            + "userMess: " + userMess + "\n"
                            + "prevQuery: " + prevQuery + "\n\n"
                            + "Return only the corrected JSON query.";

                    Prompt comparePrompt = new Prompt(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                    );


                    ChatOptions retryChatOptions = ChatOptions.builder()
                        .temperature(0.3D)
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
                        System.out.println("[AiQueryService] Failed to parse as RequestBody, trying raw JSON: " + parseException.getMessage());

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
                            new ObjectMapper().readTree(newQuery);
                            System.out.println("[AiQueryService] Successfully parsed raw JSON response");
                        } catch (Exception jsonException) {
                            System.out.println("[AiQueryService] Raw response is not valid JSON: " + jsonException.getMessage());
                            throw new RuntimeException("AI returned invalid JSON: " + newQuery, jsonException);
                        }
                    }
                    System.out.println("[AiQueryService] 🔧 Generated new query with error fix: " + newQuery);

                    // Validate syntax của query mới trước khi sử dụng
                    String newQueryValidationError = validateQuerySyntax(newQuery);
                    if (newQueryValidationError != null) {
                        System.out.println("[AiQueryService] WARNING: New query has syntax errors: " + newQueryValidationError);
                        return new String[]{
                            "❌ **Elasticsearch Error (Invalid Retry Query)**\n\n" +
                                "AI tạo ra query mới nhưng có lỗi syntax.\n\n" +
                                "**Lỗi gốc:** " + errorDetails + "\n\n" +
                                "**Lỗi query mới:** " + newQueryValidationError + "\n\n" +
                                "💡 **Gợi ý:** Vui lòng thử câu hỏi khác với cách diễn đạt khác.",
                            query
                        };
                    }

                    // Kiểm tra xem query mới có khác query cũ không
                    if (newQuery.equals(prevQuery)) {
                        System.out.println("[AiQueryService] WARNING: New query is identical to failed query");
                        return new String[]{
                            "❌ **Elasticsearch Error (Same Query Generated)**\n\n" +
                                "AI tạo ra query giống hệt với query đã lỗi.\n\n" +
                                "**Lỗi gốc:** " + errorDetails + "\n\n" +
                                "💡 **Gợi ý:** Vui lòng thử câu hỏi khác với cách diễn đạt khác.",
                            query
                        };
                    }

                    // Retry với query mới
                    System.out.println("[AiQueryService] 🔄 Đang thử lại với query đã sửa...");
                    String retryContent = logApiService.search("logs-*", newQuery);
                    System.out.println("[AiQueryService] ✅ Retry successful with corrected query");
                    return new String[]{retryContent, newQuery};

                } catch (Exception retryE) {
                    System.out.println("[AiQueryService] Retry also failed: " + retryE.getMessage());

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
     */
    private String extractElasticsearchError(String errorMessage) {
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
            return errorMessage.length() > 200 ? errorMessage.substring(0, 200) + "..." : errorMessage;
        }
    }

    /**
     * Validate Elasticsearch query syntax before sending to Elasticsearch
     */
    private String validateQuerySyntax(String query) {
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(query);

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

                    if (boolNode.has("must_not")) {
                        JsonNode mustNotNode = boolNode.get("must_not");
                        if (!mustNotNode.isArray()) {
                            return "Bool must_not must be an array";
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
     * Attempt to fix query structure issues
     */
    private String fixQueryStructure(String query) {
        try {
            // First try to heal basic JSON issues
            String healedQuery = healJsonString(query);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(healedQuery);

            // Fix: Ensure bool must/should/filter/must_not are arrays (recursively)
            JsonNode fixedNode = fixNestedBoolClauses(jsonNode, mapper);

            String fixedQueryString = mapper.writeValueAsString(fixedNode);
            if (!fixedQueryString.equals(healedQuery)) {
                System.out.println("[AiQueryService] ✅ Fixed query structure - converted bool clauses to arrays");
                System.out.println("[AiQueryService] 📝 Original length: " + query.length() + ", Fixed length: " + fixedQueryString.length());
            }
            return fixedQueryString;

        } catch (Exception e) {
            System.out.println("[AiQueryService] ❌ Failed to fix query structure: " + e.getMessage());
            System.out.println("[AiQueryService] 📝 Query length: " + query.length());
            return query; // Return original query if fix fails
        }
    }

    /**
     * Attempt to heal basic JSON string issues
     */
    private String healJsonString(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        // Count braces to find imbalances
        int openBraces = 0;
        int closeBraces = 0;
        int openBrackets = 0;
        int closeBrackets = 0;

        for (char c : json.toCharArray()) {
            if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
            else if (c == '[') openBrackets++;
            else if (c == ']') closeBrackets++;
        }

        String healed = json;

        // Add missing closing braces
        for (int i = 0; i < (openBraces - closeBraces); i++) {
            healed += "}";
        }

        // Add missing closing brackets
        for (int i = 0; i < (openBrackets - closeBrackets); i++) {
            healed += "]";
        }

        if (!healed.equals(json)) {
            System.out.println("[AiQueryService] 🔨 Healed JSON - Added " + (openBraces - closeBraces) + " braces and " + (openBrackets - closeBrackets) + " brackets");
        }

        return healed;
    }

    /**
     * Recursively fix nested bool clauses to ensure must/should/filter/must_not are arrays
     */
    private JsonNode fixNestedBoolClauses(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (JsonNode item : node) {
                arrayNode.add(fixNestedBoolClauses(item, mapper));
            }
            return arrayNode;
        }

        if (node.isObject()) {
            ObjectNode objectNode = mapper.createObjectNode();

            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();

                // If this is a bool clause, fix it
                if ("bool".equals(fieldName) && fieldValue.isObject()) {
                    ObjectNode boolObject = mapper.createObjectNode();
                    fieldValue.fields().forEachRemaining(boolEntry -> {
                        String boolKey = boolEntry.getKey();
                        JsonNode boolValue = boolEntry.getValue();

                        if (("must".equals(boolKey) || "should".equals(boolKey) ||
                            "filter".equals(boolKey) || "must_not".equals(boolKey))) {
                            // These fields must be arrays
                            if (boolValue.isArray()) {
                                ArrayNode fixedArray = mapper.createArrayNode();
                                for (JsonNode item : boolValue) {
                                    fixedArray.add(fixNestedBoolClauses(item, mapper));
                                }
                                boolObject.set(boolKey, fixedArray);
                            } else {
                                // Convert to array
                                ArrayNode arrayNode = mapper.createArrayNode();
                                arrayNode.add(fixNestedBoolClauses(boolValue, mapper));
                                boolObject.set(boolKey, arrayNode);
                            }
                        } else {
                            boolObject.set(boolKey, fixNestedBoolClauses(boolValue, mapper));
                        }
                    });
                    objectNode.set("bool", boolObject);
                } else {
                    objectNode.set(fieldName, fixNestedBoolClauses(fieldValue, mapper));
                }
            });

            return objectNode;
        }

        return node;
    }


}
