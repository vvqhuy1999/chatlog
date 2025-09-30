package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.SchemaHint;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý chuyển đổi query của người dùng sang Elasticsearch DSL
 * Bao gồm: tạo query, validate, sửa lỗi, và thực hiện tìm kiếm
 */
@Service
public class AiQueryService {
    
    @Autowired
    private LogApiService logApiService;
    
    private final ChatClient chatClient;
    
    public AiQueryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    
    /**
     * Tạo chuỗi thông tin ngày tháng cho system message với các biểu thức thời gian tương đối của Elasticsearch
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
     * Tạo Elasticsearch query từ yêu cầu của người dùng
     * @param sessionId ID phiên chat
     * @param chatRequest Yêu cầu từ người dùng
     * @return RequestBody chứa query Elasticsearch
     */
    public RequestBody generateElasticsearchQuery(Long sessionId, ChatRequest chatRequest) {
        LocalDateTime now = LocalDateTime.now();
        String dateContext = generateDateContext(now);
        
        // Sử dụng QueryPromptTemplate với các placeholder thông qua PromptConverter
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
        UserMessage sampleLogMsg = new UserMessage("SAMPLE LOG (for inference):\n" + SchemaHint.examplelog());
        UserMessage userMessage = new UserMessage(chatRequest.message());
        
        Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, sampleLogMsg, userMessage));
        
        ChatOptions chatOptions = ChatOptions.builder()
            .temperature(0.0D)
            .build();
        
        try {
            String queryConversationId = sessionId + "_query_generation";
            
            System.out.println("[AiQueryService] 🤖 Đang gọi AI để tạo Elasticsearch query...");
            RequestBody requestBody = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .advisors(advisorSpec -> advisorSpec.param(
                    ChatMemory.CONVERSATION_ID, queryConversationId
                ))
                .call()
                .entity(new ParameterizedTypeReference<>() {});
            
            // Đảm bảo query luôn là 1 (bắt buộc tìm kiếm)
            if (requestBody.getQuery() != 1) {
                System.out.println("[AiQueryService] Setting query=1 (was " + requestBody.getQuery() + ")");
                requestBody.setQuery(1);
            }
            
            System.out.println("[AiQueryService] Generated query body: " + requestBody.getBody());
            return requestBody;
            
        } catch (Exception e) {
            System.out.println("[AiQueryService] ❌ ERROR: Failed to generate query: " + e.getMessage());
            throw new RuntimeException("Failed to generate Elasticsearch query", e);
        }
    }
    
    /**
     * Kiểm tra và sửa định dạng phần thân (body) JSON của truy vấn
     */
    public String checkBodyFormat(RequestBody requestBody) {
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(requestBody.getBody());
            
            // Validate that it's a proper Elasticsearch query
            if (!jsonNode.has("query") && !jsonNode.has("aggs")) {
                System.out.println("[AiQueryService] ERROR: Missing 'query' or 'aggs' field!");
                return "❌ AI model trả về query không hợp lệ. Cần có 'query' hoặc 'aggs' field.";
            }
            
        } catch (Exception e) {
            System.out.println("[AiQueryService] ERROR: Invalid JSON format from AI!");
            System.out.println("[AiQueryService] Expected: JSON object with 'query' or 'aggs' field");
            System.out.println("[AiQueryService] Received: " + requestBody.getBody());
            System.out.println("[AiQueryService] Error details: " + e.getMessage());
            return "❌ AI model trả về format không đúng. Cần JSON query (một object duy nhất), nhận được: " + requestBody.getBody();
        }
        
        return null;
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
            String content = logApiService.search("logs-fortinet_fortigate.log-default*", query);
            System.out.println("[AiQueryService] Elasticsearch response received successfully");
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
                    String allFields = logApiService.getAllField("logs-fortinet_fortigate.log-default*");
                    String prevQuery = requestBody.getBody();
                    String userMess = chatRequest.message();
                    
                     // Cải thiện prompt với error details cụ thể
                     String systemPrompt =
                         com.example.chatlog.utils.PromptTemplate.getComparisonPrompt(
                             allFields, prevQuery, userMess, generateDateContext(LocalDateTime.now())
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
                    String retryContent = logApiService.search("logs-fortinet_fortigate.log-default*", newQuery);
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
     * Kiểm tra xem kết quả từ Elasticsearch có rỗng không
     */
    public boolean isEmptyElasticsearchResult(String elasticsearchResponse) {
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(elasticsearchResponse);
            
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
            System.out.println("[AiQueryService] Error parsing Elasticsearch response for empty check: " + e.getMessage());
            return false; // If parse fails, do not block; let AI format
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
                    System.out.println("[AiQueryService] Fixed query structure - moved aggs to root level");
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
                        System.out.println("[AiQueryService] Fixed query structure - moved aggs from bool to root level");
                        return fixedQueryString;
                    }
                }
            }
            
            return query; // No fixes needed
            
        } catch (Exception e) {
            System.out.println("[AiQueryService] Failed to fix query structure: " + e.getMessage());
            return query; // Return original query if fix fails
        }
    }
    
    /**
     * Create and validate an Elasticsearch DSL query
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
}
