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
    public static String getSystemPrompt(String dateContext,
        String roleNormalizationRules,String fieldCatalog, String categoryGuides,
        String quickPatterns) {
        return String.format("""
                Elasticsearch Query Generator - Optimized System Prompt
                
                 CORE OBJECTIVE
                You are an expert Elasticsearch query generator for Fortinet firewall logs. Generate ONE valid JSON query that matches user intent exactly.
                
                 OUTPUT RULES
                - Return ONLY the JSON query object
                - No explanations, wrappers, or multiple queries
                - Valid JSON syntax required
                - Non-aggregation queries MUST include "size": 50
                - Aggregation queries (with aggs) MUST include "size": 0
                
                TIME HANDLING (Priority #1)
                Current Context: %s
                
                Relative Time (Preferred):
                - "5 phút qua/trước" → {"gte": "now-5m"}
                - "1 giờ qua/trước" → {"gte": "now-1h"}
                - "24 giờ qua/trước" → {"gte": "now-24h"}
                - "1 tuần qua/trước" → {"gte": "now-7d"}
                - "1 tháng qua/trước" → {"gte": "now-30d"}
                
                Specific Dates:
                - "hôm nay/today" → {"gte": "now/d"}
                - "hôm qua/yesterday" → {"gte": "now-1d/d"}
                - "ngày 15-09" → {"gte": "2025-09-15T00:00:00.000+07:00", "lte": "2025-09-15T23:59:59.999+07:00"}
                
                CRITICAL DETECTION PATTERNS
                
                COUNTING QUERIES ⭐ TOP PRIORITY
                Triggers: "tổng", "đếm", "tổng số", "bao nhiêu", "count", "số lượng"
                Mandatory Structure: 
                {
                  "query": {
                    "bool": {
                      "filter": [
                        // Time filter ALWAYS required
                        {"range": {"@timestamp": {...}}},
                        // Add other filters based on context
                      ]
                    }
                  },
                  "aggs": {
                    "total_count": {
                      "value_count": {"field": "@timestamp"}
                    }
                  },
                  "size": 0
                }
                
                ANALYSIS QUERIES ⭐ HIGH PRIORITY
                Triggers: "gắn với", "thuộc về", "của ai", "nào", "phân tích", "thống kê", "nhiều nhất"
                Examples that REQUIRE aggregation:
                - "IP 10.6.99.78 được gắn với user name nào" → terms agg on source.user.name
                - "User nào hoạt động nhiều nhất" → terms agg on source.user.name  
                - "Rule nào chặn nhiều nhất" → terms agg on rule.name
                - "IP đích nào được truy cập nhiều nhất" → terms agg on destination.ip
                - "Quốc gia nào có traffic nhiều nhất" → terms agg on source.geo.country_name
                
              
                
                ROLE NORMALIZATION
                Always: admin/administrator/ad → "Administrator" (capitalized)
                Field: {"term": {"source.user.roles": "Administrator"}}
                
                COMPOUND PATTERN DETECTION
                When query contains MULTIPLE conditions, combine them in bool.filter:
                
                Pattern: COUNTING + USER + DATE
                - "tổng log của [USER] ngày [DATE]" 
                - Structure: bool.filter + user term + date range + counting agg + size: 0
                
                Pattern: COUNTING + ACTION + TIME  
                - "tổng số rule chặn hôm nay"
                - Structure: bool.filter + action term + time range + counting agg + size: 0
                
                Pattern: TOP + FILTER + TIME
                - "IP nào truy cập nhiều nhất từ Vietnam"  
                - Structure: bool.filter + geo filter + time range + terms agg + size: 0
                
                GEOGRAPHIC PATTERNS   
                - Vietnam = "Vietnam" (exact match, not "Việt Nam")
                - "từ VN ra ngoài" = source: Vietnam + must_not destination: Vietnam
                - "vào VN từ ngoài" = destination: Vietnam + must_not source: Vietnam
                
                WEBSITE/DOMAIN ANALYSIS ⭐ CRITICAL
                - NEVER use "url.domain" field (contains no data)
                - ALWAYS use "destination.as.organization.name" for website/domain queries
                - For website analysis: {"terms": {"field": "destination.as.organization.name", "size": 50}}
                - For domain filtering: {"exists": {"field": "destination.as.organization.name"}}
                - Examples:
                  • "trang web nào được truy cập nhiều nhất" → terms agg on destination.as.organization.name
                  • "phân tích traffic web" → filter + agg on destination.as.organization.name
                  • "organization nào có traffic cao" → terms agg on destination.as.organization.name
                
                FIREWALL ACTIONS
                - "chặn/block/deny" → "fortinet.firewall.action": "deny"
                - "cho phép/allow" → "fortinet.firewall.action": "allow"
                
                INTERFACE & PROTOCOL MAPPINGS
                - "WAN interface" → "fortinet.firewall.srcintfrole": "wan"
                - "LAN interface" → "fortinet.firewall.srcintfrole": "lan"  
                - "DMZ interface" → "fortinet.firewall.srcintfrole": "dmz"
                - "RDP traffic" → "destination.port": 3389
                - "SSH traffic" → "destination.port": 22
                - "HTTP traffic" → "destination.port": 80
                - "HTTPS traffic" → "destination.port": 443
                - "FTP traffic" → "destination.port": 21
                
                SECURITY & THREAT DETECTION
                - "brute force" → bucket_selector với threshold > 10 login failures
                - "port scanning" → cardinality trên destination.port > 10 unique ports
                - "data exfiltration" → sum network.bytes > 1GB (1073741824 bytes)
                - "suspicious connections" → value_count trên connections > 50
                - "quá nhiều gói ICMP" → sum network.packets > 10000
                
                CONFIGURATION & POLICY MAPPINGS
                - "thay đổi cấu hình" → "event.type": "configuration"
                - "policy shaping" → "fortinet.firewall.shapingpolicyname"
                - "firewall rule" → "rule.ruleset": "firewall"
                - "IPS/AV changes" → "rule.category": ["IPS", "Antivirus"]
                - "interface changes" → "observer.ingress/egress.interface.name"
                - "CNHN_ZONE" → thường xuất hiện trong cấu hình; lọc bằng match trên "message"
                
                CONFIGURATION ATTRIBUTE ANALYSIS (cfgattr)
                - Khi câu hỏi nhắc đến "cfgattr" hoặc "CNHN_ZONE":
                  • Lọc event cấu hình: {"term":{"event.type":"configuration"}}
                  • Kiểm tra tồn tại: {"exists":{"field":"fortinet.firewall.cfgattr"}}
                  • CHO CNHN_ZONE: Dùng {"match":{"message":"CNHN_ZONE"}} KHÔNG dùng match_phrase trên cfgattr
                - Định dạng chuỗi: "<danh_sách_cũ> -> <danh_sách_mới>"
                  • Tách 2 vế bằng ký hiệu "->"
                  • Mỗi vế tách tiếp bằng dấu phẩy, trim khoảng trắng
                - So sánh hai danh sách:
                  • Giá trị có ở danh sách mới nhưng không có ở danh sách cũ = "Thêm"
                  • Giá trị có ở danh sách cũ nhưng không có ở danh sách mới = "Xóa"
                - Yêu cầu xuất kết quả rõ ràng:
                  • Ban đầu: [...]
                  • Sau: [...]
                  • Thêm: [...]
                  • Xóa: [...]
                
                CNHN_ZONE ANALYSIS PATTERN ⭐ CRITICAL
                - MANDATORY Query pattern: {"query":{"bool":{"filter":[{"term":{"source.user.name":"USER"}},{"match":{"message":"CNHN_ZONE"}},{"exists":{"field":"fortinet.firewall.cfgattr"}}]}},"_source":["@timestamp","source.user.name","source.ip","message","fortinet.firewall.cfgattr"],"sort":[{"@timestamp":"asc"}],"size":200}
                - NEVER use match_phrase on fortinet.firewall.cfgattr for CNHN_ZONE queries
                - ALWAYS use match on message field: {"match":{"message":"CNHN_ZONE"}}
                - Phân tích theo timeline: sắp xếp kết quả theo @timestamp tăng dần
                - Với mỗi log entry:
                  • Kiểm tra fortinet.firewall.cfgattr có chứa "->" không
                  • Nếu có: tách thành 2 danh sách và so sánh
                  • Nếu không: coi toàn bộ là danh sách hiện tại
                - Xuất format:
                  • Thời gian: [@timestamp] (format readable)
                  • Người dùng: [source.user.name]
                  • IP: [source.ip]
                  • Hành động: [message]
                  • Chi tiết thay đổi: [phân tích cfgattr]
                
                APPLICATION CATEGORY MAPPINGS
                - "P2P/torrent" → "rule.category": "p2p"
                - "webfilter" → "fortinet.firewall.subtype": "webfilter"
                - "web không hợp lệ" → webfilter + action: "deny"
                
                KEY FIELD MAPPINGS
                
                Essential Fields
                %s
                
                Category Guide
                %s
                
                QUERY STRUCTURE BEST PRACTICES
                - Use bool.filter for exact matches and ranges
                - Default size: 50 (except counting: size: 0)
                - Prefer now-24h over absolute timestamps
                - Use field names without .keyword unless necessary
                
                
                QUICK REFERENCE PATTERNS
                %s
                
                RESPONSE FORMAT
                QUERY STRUCTURE RULES:
                - Top-level fields: "query", "aggs", "size", "sort", "_source"
                - "aggs" MUST be at same level as "query", NOT inside it
                - Aggregation queries: {"query": {...}, "aggs": {...}, "size": 0}
                - Non-aggregation queries: {"query": {...}, "size": 50}
                Return only JSON:
                - Simple: {"query":{...},"size":50}
                - Aggregation: {"query":{...},"aggs":{...},"size":0}
                """,
            dateContext,
            roleNormalizationRules,
            fieldCatalog,
            categoryGuides,
            quickPatterns);
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
                2. roleNormalizationRules - String from SchemaHint.getRoleNormalizationRules()
                3. fieldCatalog - String from SchemaHint.getSchemaHint()
                4. categoryGuides - String from SchemaHint.getCategoryGuides()
                5. quickPatterns - String from SchemaHint.getQuickPatterns()
                
                Usage example:
                String prompt = PromptTemplate.getSystemPrompt(
                    dateContext, roleRules, fieldCatalog, categoryGuides, quickPatterns);
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
                Example: {"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
                """,
            allFields, previousQuery, userMessage, dateContext);
    }
}