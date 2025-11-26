package com.example.chatlog.utils;

import java.util.Map;
import java.util.HashMap;

/**
 * Lớp tiện ích để tạo prompt cho việc sinh truy vấn Elasticsearch
 * Sử dụng dynamic examples từ knowledge base
 */
public class QueryPromptTemplate {
    
    /**
     * Template cho prompt sinh truy vấn Elasticsearch với dynamic examples
     */
    public static final String QUERY_GENERATION_TEMPLATE = """
            Elasticsearch Query Generator - Fortinet Firewall Logs
                        
            CORE OBJECTIVE
            You are an expert Elasticsearch query generator for Fortinet logs. Your task is to generate ONE valid JSON query that matches the user's intent exactly.
                               
            TIME HANDLING (Priority #1)
            Current Context: {dateContext}
                        
            Relative Time (Preferred):
                        
            "5 phút qua/trước" → {"gte": "now-5m"}
                        
            "1 giờ qua/trước" → {"gte": "now-1h"}
                        
            "24 giờ qua/trước" → {"gte": "now-24h"}
                        
            "1 tuần qua/trước" → {"gte": "now-7d"}
                        
            "1 tháng qua/trước" → {"gte": "now-30d"}
                        
            Specific Dates:
                        
            "hôm nay/today" → {"gte": "now/d"}
                        
            "hôm qua/yesterday" → {"gte": "now-1d/d"}
                        
            "ngày DD-MM" → {"gte": "YYYY-MM-DDT00:00:00Z", "lte": "YYYY-MM-DDT23:59:59Z"}
                         
            SCHEMA INFORMATION
            {schemaInfo}
            
            ROLE NORMALIZATION RULES
            {roleNormalizationRules}
            
            EXAMPLE LOG STRUCTURE
            {exampleLog}
            
            FORTINET ACTION RULES
            {fortinetActionRules}
            
            USER QUERY: {userQuery}
            
            DYNAMIC EXAMPLES FROM KNOWLEDGE BASE
            {dynamic_examples}
            
        🚨 MANDATORY BUSINESS RULES (PRIORITY #0 - MUST FOLLOW) 🚨
         
           1. IF QUERY CONTAINS: "internet", "ra ngoài", "outbound", "băng thông", "lưu lượng"
           THEN YOU MUST ADD THESE FILTERS:
           "terms": {
             "observer.egress.interface.name": ["sdwan", "port1", "port2", "FTTH-WAN1-CMC", "FTTH-WAN2-FPT"]
           }
   
            OUTPUT RULES
                        
            Return ONLY the JSON query object (valid JSON)
                        
            No explanations, wrappers, or multiple queries
                        
            Valid JSON syntax required
            
            🎯 TOP-LEVEL STRUCTURE (MANDATORY)
            All queries MUST follow this root-level structure:
            {
              "query": { ... },      // Query logic (bool, term, range, etc.)
              "aggs": { ... },       // Aggregations (optional, for grouping/stats)
              "size": 50,            // Number of results (0 for aggs, 50 for search)
              "sort": [ ... ],       // Sort order (optional)
              "_source": [ ... ]     // Fields to return (optional)
            }
            
            ❌ COMMON MISTAKES (WRONG STRUCTURE):
            
            ❌ WRONG: "size" inside "query"
            {
              "query": {
                "bool": {
                  "filter": [...]
                },
                "size": 50  // ← INVALID! Must be at root level
              }
            }
            
            ❌ WRONG: "aggs" inside "query"
            {
              "query": {
                "bool": {
                  "filter": [...]
                },
                "aggs": {  // ← INVALID! Must be at root level
                  "my_agg": {...}
                }
              }
            }
            
            ❌ WRONG: Bool clauses not as arrays
            {
              "query": {
                "bool": {
                  "filter": {"term": {...}}  // ← INVALID! Must be array
                }
              }
            }
            
            ✅ CORRECT:
            {
              "query": {
                "bool": {
                  "filter": [  // ← Must be array
                    {"term": {...}}
                  ]
                }
              },
              "size": 50
            }
            
            🔥 BOOL QUERY STRUCTURE RULES
            All bool clauses MUST be ARRAYS:
            {
              "query": {
                "bool": {
                  "must": [       // ← Array
                    {...},
                    {...}
                  ],
                  "should": [     // ← Array
                    {...},
                    {...}
                  ],
                  "filter": [     // ← Array
                    {...},
                    {...}
                  ],
                  "must_not": [   // ← Array
                    {...}
                  ]
                }
              },
              "size": 50
            }
            
            Even with single condition, MUST use array:
            ✅ CORRECT: "filter": [{"term": {"field": "value"}}]
            ❌ WRONG:   "filter": {"term": {"field": "value"}}
            
            📊 AGGREGATION STRUCTURE RULES
            Sub-aggregations go inside parent agg:
            {
              "aggs": {
                "parent_agg": {
                  "terms": {"field": "user.name"},
                  "aggs": {              // ← Sub-aggs inside parent
                    "child_agg_1": {
                      "sum": {"field": "bytes"}
                    },
                    "child_agg_2": {
                      "avg": {"field": "duration"}
                    }
                  }
                }
              },
              "size": 0
            }
            
            Multiple root-level aggs:
            {
              "query": {...},
              "aggs": {
                "agg_1": {...},
                "agg_2": {...},
                "agg_3": {...}
              },
              "size": 0
            }
            
            ⚠️ VALIDATION CHECKLIST
            Before generating any query, verify:
            ✅ Is "size" at root level? (NOT inside "query")
            ✅ Is "aggs" at root level? (NOT inside "query")
            ✅ Are all bool clauses (must, should, filter, must_not) ARRAYS?
            ✅ Is the JSON valid? (Check brackets, commas, quotes)
            ✅ For search queries: "size": 50
            ✅ For aggregation queries: "size": 0
            
            If ANY check fails → Query is INVALID!
            
            🎯 SIZE FIELD RULES
            - Search queries (no aggregations): "size": 50
            - Aggregation queries: "size": 0
            - Mixed queries (search + aggs): Use appropriate size based on primary intent
            
            Examples:
            // Search only
            {"query": {...}, "size": 50}
            
            // Aggregation only
            {"query": {...}, "aggs": {...}, "size": 0}
            
            // Get top 10 results + aggregation stats
            {"query": {...}, "aggs": {...}, "size": 10}
            
            📝 TEMPLATE TO FOLLOW
            {
              "query": {
                "bool": {
                  "filter": [
                    // Add filter conditions here as array items
                  ],
                  "must": [
                    // Add must conditions here as array items (if needed)
                  ],
                  "should": [
                    // Add should conditions here as array items (if needed)
                  ],
                  "must_not": [
                    // Add must_not conditions here as array items (if needed)
                  ]
                }
              },
              "aggs": {
                // Add aggregations here at ROOT level (if needed)
              },
              "_source": [
                // Add fields to return (if needed)
              ],
              "sort": [
                // Add sort criteria (if needed)
              ],
              "size": 50  // Or 0 for aggs-only queries
            }
            
            KEY RULES:
            ✅ "query", "aggs", "size", "sort", "_source" are ROOT-LEVEL fields
            ✅ They are SIBLINGS (same level), NOT nested
            ✅ "size" is NEVER inside "query"
            ✅ "aggs" is NEVER inside "query"
            ✅ All bool clauses MUST be ARRAYS, even with single item
            
            OUTPUT REQUIREMENTS:
            - Return ONLY the JSON query object (valid JSON)
            - No explanations, wrappers, or multiple queries
            - Valid JSON syntax required
            
            """;
    
    /**
     * Tạo prompt cho việc sinh truy vấn Elasticsearch với dynamic examples
     * 
     * @param userQuery Câu truy vấn của người dùng
     * @param dateContext Ngữ cảnh thời gian hiện tại
     * @param schemaInfo Thông tin schema
     * @param roleNormalizationRules Quy tắc chuẩn hóa vai trò
     * @param exampleLog Ví dụ cấu trúc log thực tế
     * @param fortinetActionRules Quy tắc viết hoa cho fortinet.firewall.action
     * @param dynamicExamples Các ví dụ động từ knowledge base
     * @return Prompt đã được tạo với các placeholder đã được thay thế
     */
    public static String createQueryGenerationPrompt(String userQuery, String dateContext,
                                                    String schemaInfo, String roleNormalizationRules, 
                                                    String exampleLog, String fortinetActionRules,
                                                    String dynamicExamples) {
        Map<String, Object> params = new HashMap<>();
        params.put("userQuery", userQuery);
        params.put("dateContext", dateContext);
        params.put("schemaInfo", schemaInfo);
        params.put("roleNormalizationRules", roleNormalizationRules);
        params.put("exampleLog", exampleLog);
        params.put("fortinetActionRules", fortinetActionRules);
        params.put("dynamic_examples", dynamicExamples);
        
        return formatTemplate(QUERY_GENERATION_TEMPLATE, params);
    }
    
    /**
     * Thay thế các placeholder trong template
     * 
     * @param template Template chuỗi với các placeholder dạng {name}
     * @param params Map chứa các cặp key-value để thay thế placeholder
     * @return Chuỗi đã được thay thế placeholder
     */
    private static String formatTemplate(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * Template cho prompt so sánh và tái tạo query khi không có kết quả
     */
    public static final String COMPARISON_PROMPT_TEMPLATE = """
            You are an Elasticsearch Query Generator. Re-generate the query to match the user request better.
            
            CRITICAL RULES - FOLLOW EXACTLY:
            1. MUST return ONLY direct Elasticsearch JSON query (no wrapper)
            2. ALWAYS use 'Z' or '+00:00' timezone format in timestamps (UTC)
            3. ALWAYS return single-line JSON without line breaks
            4. NEVER use RequestBody wrapper format
            5. Return clean JSON structure as single continuous string
            6. CRITICAL: Return ONLY ONE JSON object, NOT multiple objects separated by commas
            7. ALL fields (query, aggs, sort, size) MUST be in the SAME JSON object
            8. NEVER return: {"query":{...}},{"aggs":{...}} - This is WRONG!
            9. ALWAYS return: {"query":{...},"aggs":{...}} - This is CORRECT!
            10. Bool clauses (must/should/filter/must_not) MUST ALWAYS be ARRAYS
                ✅ CORRECT: "filter": [{"term": {...}}]
                ❌ WRONG: "filter": {"term": {...}}
            11. "aggs" MUST be at ROOT level, NOT inside "query"
                ✅ CORRECT: {"query": {...}, "aggs": {...}}
                ❌ WRONG: {"query": {"bool": {...}, "aggs": {...}}}
            
            TIMESTAMP FORMAT:
            - CORRECT: "2025-09-14T11:41:04.000Z"
            - INCORRECT: "2025-09-14T11:41:04.000+07:00"
            
            Available fields: %s
            
            Previous query that returned 0 results: %s
            
            User request: %s
            
            Current date context: %s
            
            ANALYSIS INSTRUCTIONS:
            1. Analyze why the previous query returned 0 results
            2. Check field names for typos or incorrect mappings
            3. Verify timestamp ranges are correct for the current date
            4. Consider if the search criteria might be too restrictive
            5. Look for alternative field names that might match the user intent
            
            RESPONSE FORMAT:
            Return ONLY the corrected Elasticsearch JSON query, no explanations.
            Example: {"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
            """;
    
    /**
     * Tạo prompt cho việc so sánh và tái tạo query khi không có kết quả
     * 
     * @param allFields Danh sách tất cả fields có sẵn
     * @param previousQuery Query trước đó
     * @param userMessage Tin nhắn người dùng
     * @param dateContext Ngữ cảnh ngày tháng
     * @return Prompt đã được format
     */
    public static String getComparisonPrompt(String allFields, String previousQuery, 
                                            String userMessage, String dateContext) {
        return String.format(COMPARISON_PROMPT_TEMPLATE, 
            allFields, previousQuery, userMessage, dateContext);
    }
}
