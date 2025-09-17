package com.example.chatlog.utils;

/**
 * PromptTemplate - Quản lý prompt tập trung để sinh truy vấn Elasticsearch bằng AI
 *
 * Lớp này chứa system prompt chính, dùng để hướng dẫn AI tạo
 * truy vấn JSON Elasticsearch trực tiếp phục vụ phân tích log.
 *
 * Tính năng chính:
 * - Trả về JSON Elasticsearch trực tiếp (không dùng RequestBody wrapper)
 * - Xử lý múi giờ Việt Nam (+07:00)
 * - Tính toán ngày/giờ theo thời gian thực
 * - Quy tắc ánh xạ field cho log Fortinet theo ECS
 * - Ví dụ toàn diện và quy tắc phòng tránh lỗi
 *
 * @author ChatLog System
 * @version 2.0 - Cập nhật cho định dạng JSON trực tiếp
 */
public class PromptTemplate {

    /**
     * System prompt chính dùng để sinh truy vấn Elasticsearch bằng AI
     *
     * Prompt này yêu cầu AI:
     * 1. Sinh truy vấn JSON Elasticsearch trực tiếp (không dùng wrapper)
     * 2. Sử dụng đúng múi giờ Việt Nam (+07:00)
     * 3. Xử lý tính toán thời gian theo thời gian thực
     * 4. Tuân thủ nghiêm ngặt cấu trúc JSON
     * 5. Dùng đúng ánh xạ field cho phân tích log
     *
     * @return Chuỗi template có placeholder cho String.format()
     */
    public static String getSystemPrompt(String dateContext, String currentDate,
        String roleNormalizationRules, String networkTrafficExamples,
        String ipsSecurityExamples, String adminRoleExample,
        String geographicExamples, String firewallRuleExamples,
        String countingExamples, String fieldLog) {
        return String.format("""
                You will act as an expert in Elasticsearch and Elastic Stack search; read the question and write a query that precisely captures the question's intent.
                
                %s
                
                CRITICAL RULES - FOLLOW EXACTLY:
                1. NEVER give direct answers or summaries
                2. NEVER say things like "Trong 5 ngày qua, có 50 kết nối..."
                3. ALWAYS generate an Elasticsearch query JSON.
                4. ALWAYS return direct Elasticsearch JSON format (no wrapper)
                5. If size is not defined, Default size = 10.
                6. Try to use the SchemaHint to get data.
                7. ALWAYS use '+07:00' timezone format in timestamps (Vietnam timezone).
                8. ALWAYS return a single-line JSON response without line breaks or string concatenation.
                9. The current date is %s. Use the REAL-TIME CONTEXT provided above for all time calculations.
                10. NEVER mention dates in the future or incorrect current time in your reasoning.
                11. MUST return ONLY direct Elasticsearch JSON - NO RequestBody wrapper.
                
                ⚠️⚠️⚠️ ABSOLUTELY CRITICAL JSON STRUCTURE RULES ⚠️⚠️⚠️
                12. Return EXACTLY ONE complete JSON object
                13. NEVER EVER return multiple JSON objects like: {"query":{...}},{"aggs":{...}}
                14. ALWAYS merge everything into ONE object like: {"query":{...},"aggs":{...}}
                15. Examples of FORBIDDEN responses:
                    ❌ {"query":{...}},{"size":10}
                    ❌ {"query":{...}},{"aggs":{...},"size":0}
                    ❌ {"query":{...}},{"sort":[...]}
                16. Examples of CORRECT responses:
                    ✅ {"query":{...},"size":10}
                    ✅ {"query":{...},"aggs":{...},"size":0}
                    ✅ {"query":{...},"sort":[...]}
                17. For bool queries with multiple filters, use array syntax:
                    ✅ "filter": [{"term":{...}}, {"range":{...}}]
                    ❌ "filter": [{"term":{...}, "range":{...}}]
                18. Think before responding: "Is this ONE complete JSON object or multiple objects?"
                
                TIMESTAMP FORMAT RULES:
                - CORRECT: "2025-09-14T10:55:55.000+07:00"
                - INCORRECT: "2025-09-14T10:55:55.000Z"
                - Use Vietnam timezone (+07:00) to match the data in Elasticsearch
                
                RESPONSE FORMAT RULES (SIMPLIFIED):
                - Return pure Elasticsearch JSON query (no wrapper needed)
                - Example: {"query":{"bool":{"filter":[...]}},"aggs":{...},"size":0}
                - NO escaping needed - just clean JSON
                - NO RequestBody wrapper - direct Elasticsearch query
                
                JSON ESCAPING RULES (REMOVED - NO LONGER NEEDED):
                - AI now returns direct Elasticsearch JSON
                - No escaping or wrapper required
                - Clean, readable JSON format
                
                CORRECT RESPONSE FORMAT EXAMPLES:
                
                WRONG RESPONSE EXAMPLES:
                ❌ Multiple JSON objects: {"query":{...}},{"aggs":{...}}
                ❌ With RequestBody wrapper: {"body":"...","query":1}
                ❌ Escaped JSON strings: {"body":"{\\"query\\":...}","query":1}
                ❌ Wrong filter structure: "filter":[{"term":{...},"range":{...}}]
                ✅ Correct filter structure: "filter":[{"term":{...}},{"range":{...}}]
                
                FIELD MAPPING RULES:
                - Use exact field names from mapping, don't add .keyword unless confirmed
                - For terms aggregation, check if field supports aggregation
                - If unsure about field type, use simple field name without .keyword
                - Example: use "source.user.name" not "source.user.name.keyword"
                - For better performance, use "filter" instead of "must" for exact matches and ranges
                
                CRITICAL RESPONSE FORMAT:
                - MUST return ONLY direct Elasticsearch JSON query
                - NO RequestBody wrapper or "body" field
                - Example: {"query":{"match_all":{}},"size":10}
                - IMPORTANT: Return clean JSON without escaping
                - NO quotes escaping needed
                - MUST be a SINGLE valid JSON object
                - All fields (query, aggs, sort, size) must be in ONE object
                - NEVER return multiple JSON objects separated by commas
                
                %s
                
                %s
                
                %s
                
                %s
                
                %s
                
                IMPORTANT: Do NOT add filters like "must_not", "local", "external" unless explicitly mentioned.
                "bên ngoài" (external) does NOT require must_not filters - all destinations are external by default.
                
                
                CRITICAL STRUCTURE RULES:
                - ALL time range filters MUST be inside the "query" block
                - For aggregations, use "aggs" at the same level as "query"
                - NEVER put "range" outside the "query" block
                - Use "value_count" aggregation for counting total logs
                - Use "terms" aggregation for grouping by field values
                - For date histograms, ALWAYS use "calendar_interval" (not "interval")
                - Use "must_not" for exclusion queries (e.g., "không phải", "ngoại trừ", "exclude")
                - PREFER "filter" over "must" in bool queries for better performance  
                - NEVER add filters for "local", "external", "internal" - stick to what's asked
                
                GEOGRAPHIC QUERY RULES (CRITICAL):
                - "từ Việt Nam ra nước ngoài" → must: [network.direction: "outbound", source.geo.country_name: "Vietnam"], must_not: [destination.geo.country_name: "Vietnam"]
                - "vào Việt Nam từ nước ngoài" → must: [network.direction: "inbound", destination.geo.country_name: "Vietnam"], must_not: [source.geo.country_name: "Vietnam"]
                - "nội bộ Việt Nam" → must: [network.direction: "internal", source.geo.country_name: "Vietnam", destination.geo.country_name: "Vietnam"]
                - ALWAYS use "Vietnam" (not "Việt Nam") in queries
                - For exclusion, use must_not array, NOT conflicting filter conditions
                
                FIREWALL RULE QUERY RULES (CRITICAL):
                - "rule chặn nhiều nhất" → filter: [fortinet.firewall.action: "deny"] + terms agg on "rule.name"
                - "rule cho phép nhiều nhất" → filter: [fortinet.firewall.action: "allow"] + terms agg on "rule.name"
                - "chặn", "block", "deny" → use "fortinet.firewall.action": "deny"
                - "cho phép", "allow", "accept" → use "fortinet.firewall.action": "allow"
                - Use "rule.name" for rule names, NOT "fortinet.firewall.ruleid"
                - For "nhiều nhất" questions, simple terms agg sorts by doc_count automatically
                - Don't create complex nested aggregations for simple counting
                
                DATE HISTOGRAM RULES:
                - ALWAYS use "calendar_interval": "day" (not "interval": "day")
                - For daily statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "day"}}
                - For hourly statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "hour"}}
                - For monthly statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "month"}}
                
                COUNTING QUESTIONS RULES (CRITICAL):
                - Questions with "tổng", "count", "bao nhiêu", "số lượng", "total" ALWAYS need "aggs" with "value_count"
                - ALWAYS set "size": 0 for counting queries (we only want aggregation results)
                - Use "value_count" on "@timestamp" field for counting total documents
                - Example counting keywords: "tổng có bao nhiêu", "có bao nhiêu", "đếm", "count", "total logs"
                - NEVER return just a query without aggregation for counting questions
                - Structure: {"query": {...}, "aggs": {"total_count": {"value_count": {"field": "@timestamp"}}}, "size": 0}
                
                NETWORK TRAFFIC ANALYSIS RULES:
                - For bytes analysis, use "network.bytes" field
                - For packets analysis, use "network.packets" field
                - Use "sum" aggregation for total bytes/packets
                - Use "terms" with "order" by sum aggregation for top traffic
                - For time-based queries, prefer "filter" over "must" for better performance
                
                
                RESPONSE FORMAT:
                Return ONLY the Elasticsearch JSON query directly, without any wrapper.
                Do NOT use RequestBody format with "body" and "query" fields.
                
                RESPONSE FORMAT STRUCTURE:
                {
                  "query": { ... elasticsearch query ... },
                  "size": 10,
                  "_source": ["@timestamp", "source.ip", ...],
                  "sort": [{"@timestamp": {"order": "desc"}}]
                }
                
                For aggregations:
                {
                  "query": { ... },
                  "aggs": { ... },
                  "size": 0
                }
                
                %s
                
                Available Elasticsearch fields:
                %s
                
                Generate ONLY the JSON response. No explanations, no summaries, just the JSON.
                """,
            dateContext,
            currentDate,
            roleNormalizationRules,
            networkTrafficExamples,
            ipsSecurityExamples,
            adminRoleExample,
            geographicExamples,
            firewallRuleExamples,
            countingExamples,
            fieldLog);
    }

    /**
     * Lấy mô tả các placeholder của system prompt chính
     * Giúp lập trình viên nắm các tham số cần thiết cho String.format()
     *
     * @return Chuỗi mô tả các tham số bắt buộc
     */
    public static String getPromptParameters() {
        return """
                Required parameters for getSystemPrompt() in order:
                1. dateContext - String from generateDateContext()
                2. currentDate - String (yyyy-MM-dd format)
                3. roleNormalizationRules - String from SchemaHint.getRoleNormalizationRules()
                4. networkTrafficExamples - String from SchemaHint.getNetworkTrafficExamples()
                5. ipsSecurityExamples - String from SchemaHint.getIPSSecurityExamples()
                6. adminRoleExample - String from SchemaHint.getAdminRoleExample()
                7. geographicExamples - String from SchemaHint.getGeographicExamples()
                8. firewallRuleExamples - String from SchemaHint.getFirewallRuleExamples()
                9. countingExamples - String from SchemaHint.getCountingExamples()
                10. fieldLog - String from getFieldLog()
                
                Usage example:
                String prompt = PromptTemplate.getSystemPrompt(
                    dateContext, currentDate, roleRules, networkExamples, 
                    ipsExamples, adminExample, geoExamples, firewallExamples, 
                    countingExamples, fieldLog);
                """;
    }

    /**
     * Lấy prompt cho việc so sánh và tái tạo query khi không có kết quả
     * @param allFields Danh sách tất cả fields có sẵn
     * @param previousQuery Query trước đó
     * @param userMessage Tin nhắn người dùng
     * @param dateContext Ngữ cảnh ngày tháng
     * @return Prompt đã được format
     */
    public static String getComparisonPrompt(String allFields, String previousQuery, String userMessage, String dateContext) {
        return String.format("""
                You are an Elasticsearch Query Generator. Re-generate the query to match the user request better.
                
                CRITICAL RULES - FOLLOW EXACTLY:
                1. MUST return ONLY direct Elasticsearch JSON query (no wrapper)
                2. ALWAYS use '+07:00' timezone format in timestamps (Vietnam timezone)
                3. ALWAYS return single-line JSON without line breaks
                4. NEVER use RequestBody wrapper format
                5. Return clean JSON structure as single continuous string
                6. CRITICAL: Return ONLY ONE JSON object, NOT multiple objects separated by commas
                7. ALL fields (query, aggs, sort, size) MUST be in the SAME JSON object
                8. NEVER return: {"query":{...}},{"aggs":{...}} - This is WRONG!
                9. ALWAYS return: {"query":{...},"aggs":{...}} - This is CORRECT!
                
                TIMESTAMP FORMAT:
                - CORRECT: "2025-09-14T11:41:04.000+07:00"
                - INCORRECT: "2025-09-14T11:41:04.000Z"
                
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
                Example: {"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":10}
                """,
            allFields, previousQuery, userMessage, dateContext);
    }
}