package com.example.chatlog.utils;

/**
 * PromptTemplate - Centralized prompt management for AI Elasticsearch Query Generation
 * 
 * This class contains the main system prompt used to guide AI in generating 
 * direct Elasticsearch JSON queries for log analysis.
 * 
 * Key Features:
 * - Direct Elasticsearch JSON format (no RequestBody wrapper)
 * - Vietnam timezone (+07:00) handling
 * - Real-time date/time calculations
 * - Field mapping rules for Fortinet ECS logs
 * - Comprehensive examples and error prevention
 * 
 * @author ChatLog System
 * @version 2.0 - Updated for direct JSON format
 */
public class PromptTemplate {
    
    /**
     * Main system prompt template for AI Elasticsearch query generation
     * 
     * This prompt instructs AI to:
     * 1. Generate direct Elasticsearch JSON queries (no wrapper)
     * 2. Use proper Vietnam timezone (+07:00) 
     * 3. Handle real-time date calculations
     * 4. Follow strict JSON structure rules
     * 5. Use correct field mappings for log analysis
     * 
     * @return String template with placeholders for String.format()
     */
    public static String getSystemPrompt(String dateContext, String currentDate, 
            String roleNormalizationRules, String networkTrafficExamples, 
            String ipsSecurityExamples, String adminRoleExample, String fieldLog) {
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
                
                CORRECT RESPONSE EXAMPLES (DIRECT ELASTICSEARCH JSON):
                
                Question: "Get last 10 logs from yesterday"
                Response: {"query":{"range":{"@timestamp":{"gte":"2025-09-15T00:00:00.000+07:00","lte":"2025-09-15T23:59:59.999+07:00"}}},"size":10,"sort":[{"@timestamp":{"order":"desc"}}]}
                
                Question: "hôm nay có log của QuynhTX ?"
                Response: {"query":{"bool":{"filter":[{"term":{"source.user.name":"QuynhTX"}},{"range":{"@timestamp":{"gte":"2025-09-16T00:00:00.000+07:00","lte":"2025-09-16T23:59:59.999+07:00"}}}]}},"size":10}
                
                Question: "Từ IP nguồn 10.0.0.25, đích nào nhận nhiều bytes nhất trong 24 giờ qua"
                Response: {"query":{"bool":{"filter":[{"term":{"source.ip":"10.0.0.25"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"aggs":{"top_destinations":{"terms":{"field":"destination.ip","size":10,"order":{"bytes_sum":"desc"}},"aggs":{"bytes_sum":{"sum":{"field":"network.bytes"}}}}},"size":0}
                
                Question: "Liệt kê các phiên có mức rủi ro IPS cao trong 1 ngày qua"
                Response: {"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-24h"}}},{"terms":{"fortinet.firewall.crlevel":["high","critical"]}}]}},"sort":[{"@timestamp":{"order":"desc"}}]}
                
                Question: "Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?"
                Response: {"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-24h"}}},{"term":{"event.action":"deny"}}]}},"aggs":{"top_sources":{"terms":{"field":"source.ip","size":10,"order":{"deny_count":"desc"}},"aggs":{"deny_count":{"value_count":{"field":"event.action"}}}}},"size":0}
                
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
                
                IMPORTANT FIELD MAPPINGS:
                - "tổ chức", "organization", "công ty" → use "destination.as.organization.name"
                - "người dùng", "user" → use "source.user.name"
                - "địa chỉ IP", "IP address" → use "source.ip" or "destination.ip"
                - "hành động", "action" → use "event.action"
                - "bytes", "dung lượng", "traffic" → use "network.bytes"
                - "packets", "gói tin" → use "network.packets"
                - "mức rủi ro", "risk level", "crlevel" → use "fortinet.firewall.crlevel"
                - "tấn công", "attack", "signature" → use "fortinet.firewall.attack"
                - For better performance, use "filter" instead of "must" for exact matches and ranges
                
                CRITICAL IPS FIELD MAPPINGS:
                - "crlevel" ALWAYS maps to "fortinet.firewall.crlevel" (NOT "fortinet.crlevel")
                - IPS risk levels: "low", "medium", "high", "critical"
                - For multiple values use "terms": {"fortinet.firewall.crlevel": ["high", "critical"]}
                - Time range format: use "now-24h" NOT "now-1d/d"
                
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
                
                IMPORTANT: Do NOT add filters like "must_not", "local", "external" unless explicitly mentioned.
                "bên ngoài" (external) does NOT require must_not filters - all destinations are external by default.
                
                
                CRITICAL STRUCTURE RULES:
                - ALL time range filters MUST be inside the "query" block
                - For aggregations, use "aggs" at the same level as "query"
                - NEVER put "range" outside the "query" block
                - Use "value_count" aggregation for counting total logs
                - Use "terms" aggregation for grouping by field values
                - For date histograms, ALWAYS use "calendar_interval" (not "interval")
                - NEVER use "must_not" unless explicitly asked to exclude something
                - PREFER "filter" over "must" in bool queries for better performance
                - NEVER add filters for "local", "external", "internal" - stick to what's asked
                
                DATE HISTOGRAM RULES:
                - ALWAYS use "calendar_interval": "day" (not "interval": "day")
                - For daily statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "day"}}
                - For hourly statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "hour"}}
                - For monthly statistics: {"date_histogram": {"field": "@timestamp", "calendar_interval": "month"}}
                
                COUNTING QUESTIONS RULES:
                - Questions with "tổng", "count", "bao nhiêu", "số lượng" ALWAYS need "aggs" with "value_count"
                - ALWAYS set "size": 0 for counting queries
                - Example counting keywords: "tổng có bao nhiêu", "có bao nhiêu", "đếm", "count"
                
                NETWORK TRAFFIC ANALYSIS RULES:
                - For bytes analysis, use "network.bytes" field
                - For packets analysis, use "network.packets" field
                - Use "sum" aggregation for total bytes/packets
                - Use "terms" with "order" by sum aggregation for top traffic
                - Example: {"terms": {"field": "destination.ip", "order": {"bytes_sum": "desc"}}, "aggs": {"bytes_sum": {"sum": {"field": "network.bytes"}}}}
                - For time-based queries, prefer "filter" over "must" for better performance
                
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
                Return ONLY the Elasticsearch JSON query directly, without any wrapper.
                Do NOT use RequestBody format with "body" and "query" fields.
                
                EXAMPLE CORRECT RESPONSES:
                Question: "Get last 10 logs from yesterday"
                Response: {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "2025-09-14T00:00:00.000+07:00",
                        "lte": "2025-09-14T23:59:59.999+07:00"
                      }
                    }
                  },
                  "size": 10,
                  "sort": [{"@timestamp": {"order": "desc"}}]
                }
                
                Question: "Count total logs today"
                Response: {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "2025-09-15T00:00:00.000+07:00",
                        "lte": "2025-09-15T23:59:59.999+07:00"
                      }
                    }
                  },
                  "aggs": {
                    "log_count": {
                      "value_count": {
                        "field": "@timestamp"
                      }
                    }
                  },
                  "size": 0
                }
                
                Question: "danh sách tổ chức đích mà NhuongNT truy cập"
                Response: {
                  "query": {
                    "bool": {
                      "must": [
                        {"term": {"source.user.name": "NhuongNT"}},
                        {"range": {"@timestamp": {"gte": "2025-09-15T00:00:00.000+07:00", "lte": "2025-09-15T23:59:59.999+07:00"}}}
                      ]
                    }
                  },
                  "aggs": {
                    "organizations": {
                      "terms": {"field": "destination.as.organization.name", "size": 10}
                    }
                  },
                  "size": 0
                }
                
                Question: "tổ chức bên ngoài mà user ABC truy cập"
                Response: {
                  "query": {
                    "bool": {
                      "must": [
                        {"term": {"source.user.name": "ABC"}},
                        {"range": {"@timestamp": {"gte": "2025-09-15T00:00:00.000+07:00", "lte": "2025-09-15T23:59:59.999+07:00"}}}
                      ]
                    }
                  },
                  "aggs": {
                    "external_orgs": {
                      "terms": {"field": "destination.as.organization.name", "size": 10}
                    }
                  },
                  "size": 0
                }
                
                Question: "tổng có bao nhiêu log ghi nhận từ người dùng TuNM trong ngày hôm nay"
                Response: {
                  "query": {
                    "bool": {
                      "must": [
                        {"term": {"source.user.name": "TuNM"}},
                        {"range": {"@timestamp": {"gte": "2025-09-15T00:00:00.000+07:00", "lte": "2025-09-15T23:59:59.999+07:00"}}}
                      ]
                    }
                  },
                  "aggs": {
                    "log_count": {
                      "value_count": {"field": "@timestamp"}
                    }
                  },
                  "size": 0
                }
                
                Question: "thống kê số log theo ngày trong 1 tuần"
                Response: {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "2025-09-08T00:00:00.000+07:00",
                        "lte": "2025-09-15T23:59:59.999+07:00"
                      }
                    }
                  },
                  "aggs": {
                    "logs_per_day": {
                      "date_histogram": {
                        "field": "@timestamp",
                        "calendar_interval": "day"
                      }
                    }
                  },
                  "size": 0
                }
                
                Question: "thống kê theo giờ trong ngày hôm nay"
                Response: {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "2025-09-15T00:00:00.000+07:00",
                        "lte": "2025-09-15T23:59:59.999+07:00"
                      }
                    }
                  },
                  "aggs": {
                    "logs_per_hour": {
                      "date_histogram": {
                        "field": "@timestamp",
                        "calendar_interval": "hour"
                      }
                    }
                  },
                  "size": 0
                }
                
                Question: "từ IP nguồn 10.0.0.25, đích nào nhận nhiều bytes nhất trong 24 giờ qua"
                Response: {
                  "query": {
                    "bool": {
                      "filter": [
                        {"term": {"source.ip": "10.0.0.25"}},
                        {"range": {"@timestamp": {"gte": "now-24h"}}}
                      ]
                    }
                  },
                  "aggs": {
                    "by_dst": {
                      "terms": {"field": "destination.ip", "size": 10, "order": {"bytes_sum": "desc"}},
                      "aggs": {"bytes_sum": {"sum": {"field": "network.bytes"}}}
                    }
                  },
                  "size": 0
                }
                
                Question: "top 5 IP đích nhận traffic nhiều nhất hôm nay"
                Response: {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "2025-09-16T00:00:00.000+07:00",
                        "lte": "2025-09-16T23:59:59.999+07:00"
                      }
                    }
                  },
                  "aggs": {
                    "top_destinations": {
                      "terms": {"field": "destination.ip", "size": 5, "order": {"total_bytes": "desc"}},
                      "aggs": {"total_bytes": {"sum": {"field": "network.bytes"}}}
                    }
                  },
                  "size": 0
                }
                
                Question: "Liệt kê các phiên có mức rủi ro IPS cao (crlevel = high/critical) trong 1 ngày qua"
                Response: {
                  "query": {
                    "bool": {
                      "filter": [
                        {"range": {"@timestamp": {"gte": "now-24h"}}},
                        {"terms": {"fortinet.firewall.crlevel": ["high", "critical"]}}
                      ]
                    }
                  },
                  "sort": [{"@timestamp": {"order": "desc"}}]
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
                fieldLog);
    }
    
    /**
     * Get placeholders info for the main system prompt
     * This helps developers understand what parameters are needed for String.format()
     * 
     * @return String describing the required parameters
     */
    public static String getPromptParameters() {
        return """
                Required parameters for getSystemPrompt() in order:
                1. dateContext - String from generateDateContext()
                2. currentDate - String (yyyy-MM-dd format)
                3. schemaHint1 - String from SchemaHint.getRoleNormalizationRules()
                4. schemaHint2 - String from SchemaHint.getNetworkTrafficExamples()
                5. schemaHint3 - String from SchemaHint.getIPSSecurityExamples()
                6. schemaHint4 - String from SchemaHint.getAdminRoleExample()
                7. fieldLog - String from getFieldLog()
                
                Usage example:
                String prompt = String.format(PromptTemplate.getSystemPrompt(), 
                    dateContext, currentDate, schema1, schema2, schema3, schema4, fieldLog);
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