package com.example.chatlog.service.impl;

import com.example.chatlog.config.ToolsConfig;
import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.utils.LogUtils;
import com.example.chatlog.utils.SchemaHint;
import com.example.chatlog.utils.QueryPromptTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;


/**
 * Service xử lý chế độ so sánh giữa OpenAI và OpenRouter với PARALLEL PROCESSING
 * OpenAI và OpenRouter chạy đồng thời để giảm thời gian xử lý
 */
@Service
public class AiComparisonService {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private ToolsConfig toolsConfig;


    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiComparisonService(ChatClient.Builder builder) {
        this.objectMapper = new ObjectMapper();
        this.chatClient = builder.build();
    }

    /**
     * Tạo chuỗi thông tin ngày tháng cho system message
     */
    private String generateDateContext() {
        ZonedDateTime vnTimeNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ZonedDateTime utcTimeNow = vnTimeNow.withZoneSameInstant(ZoneId.of("UTC"));
        
        // 24 giờ trước
        ZonedDateTime utc24hAgo = utcTimeNow.minusHours(24);

        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        System.out.println("[generateDateContext] 📅 REALTIME Vietnam: " + vnTimeNow.format(displayFormat));
        System.out.println("[generateDateContext] 🌍 REALTIME UTC: " + utcTimeNow.format(displayFormat));
        System.out.println("[generateDateContext] ⏪ 24h ago UTC: " + utc24hAgo.format(displayFormat));

        String dateContext = String.format("""
                ═══════════════════════════════════════════════════════════════
                🚨 MANDATORY TIMEZONE RULE (PRIORITY #0)
                ═══════════════════════════════════════════════════════════════
                
                User is in VIETNAM (UTC+7). All Elasticsearch logs are in UTC.
                
                🕐 REALTIME NOW:
                - Vietnam time NOW: %s (UTC+7)
                - UTC time NOW: %s
                - Today's date in Vietnam: %s
                
                ⚠️ CRITICAL: When user mentions "hôm nay" (today), use LAST 24 HOURS:
                
                ✅ CORRECT TIMESTAMPS FOR "HÔM NAY":
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
            utc24hAgo.format(isoFormat),
            utcTimeNow.format(isoFormat),
            utc24hAgo.format(isoFormat),
            utcTimeNow.format(isoFormat)
        );

        return dateContext;
    }

    /**
     * Build tool-based prompt for parallel execution
     */
    private String buildToolBasedPrompt(String userQuery, String dateContext, String dynamicExamples) {
        System.out.println("[buildToolBasedPrompt] 🔨 Bắt đầu xây dựng tool-based prompt...");
        System.out.println("[buildToolBasedPrompt] 👤 User Query: " + userQuery.substring(0, Math.min(50, userQuery.length())) + "...");
        System.out.println("[buildToolBasedPrompt] 📅 Date Context Length: " + dateContext.length());
        System.out.println("[buildToolBasedPrompt] 📚 Dynamic Examples Length: " + (dynamicExamples != null ? dynamicExamples.length() : 0));

        String prompt = String.format("""
            You are HPT.AI - an expert Elasticsearch query assistant for Fortinet Firewall logs.
            
            ═══════════════════════════════════════════════════════════════
            🔧 TOOL-BASED WORKFLOW
            ═══════════════════════════════════════════════════════════════
            
            You have access to the "searchElasticsearch" tool to query Fortinet logs.
            
            MANDATORY WORKFLOW:
            
            STEP 1: 📝 ANALYZE user's question
            - Understand what data they need
            - Identify time range, filters, aggregations needed
            
            STEP 2: 🔧 GENERATE & CALL searchElasticsearch tool
            - Generate valid Elasticsearch DSL query
            - Call: searchElasticsearch(dslQuery="<your generated query>")
            - Tool will execute query and return structured response
            
            STEP 3: 📊 ANALYZE data and PROVIDE COMPLETE ANSWER
            - Tool returns one of: SUCCESS (with data), NO DATA, or ERROR
            - For SUCCESS: Parse and analyze the data
            - For NO DATA: Explain and suggest adjustments  
            - For ERROR: Identify issue and provide guidance
            
            STEP 4: 📋 PROVIDE NATURAL, CONVERSATIONAL ANSWER
            
            ✅ RESPONSE STRUCTURE (MUST INCLUDE ALL):
            
            1. **Direct Answer** (1-2 sentences) - Trả lời ngay câu hỏi của user
            2. **Key Insights** (narrative style) - Kể chuyện với data, highlight patterns
            3. **Supporting Details** (if needed) - Số liệu bổ sung, trends, comparisons
            4. **Lý do chọn các trường** (REQUIRED) - 3-6 bullet points explaining field choices
            5. **Query đã sử dụng** (code block) - Show the Elasticsearch query
            
            📊 IMPORTANT DATA HANDLING RULES:
            
            BYTE CONVERSION (Auto-convert to readable units):
            - CRITICAL: If value is in scientific notation (e.g., 4.199510429E9, 1.8275531163E10, 1.771889792704E12),
              MUST convert to decimal/base-10 first before calculating GB/MB/KB
            - >= 1,073,741,824 bytes → X.XX GB (show original in parentheses)
            - >= 1,048,576 bytes → X.XX MB (show original in parentheses)
            - >= 1,024 bytes → X.XX KB (show original in parentheses)
            - < 1,024 bytes → keep as bytes
            
            Example conversions:
            - 4.199510429E9 → 4,199,510,429 bytes → 3.91 GB (4,199,510,429 bytes)
            - 1.8275531163E10 → 18,275,531,163 bytes → 17.02 GB (18,275,531,163 bytes)
            - 1.771889792704E12 → 1,771,889,792,704 bytes → 1.61 TB (1,771,889,792,704 bytes)
            - Regular: "140.93 GB (151,234,567,890 bytes)" or "52.42 MB (54,976,546 bytes)"
            
            DEDUPLICATION & SUMMARIZATION:
            - If >5 similar logs (same user, IP, port, action, rule): Group them
            - Format: "User X từ IP Y truy cập Z (lặp lại 15 lần trong khoảng thời gian...)"
            - If >30 entries: Show top 20 + "... và N bản ghi tương tự khác"
            - Focus on patterns, not listing everything
            
            DATA EXTRACTION PRIORITY:
            - Action: fortinet.firewall.action → event.action → action
            - User: source.user.name → user.name
            - Bytes: network.bytes (always convert to GB/MB/KB)
            - IP: source.ip, destination.ip
            - Time: @timestamp (format as DD/MM/YYYY HH:mm:ss for Vietnam)
            - Protocol: network.protocol
            - Port: destination.port
            
            SPECIAL CASE - FORTINET CFGATTR ANALYSIS:
            If fortinet.firewall.cfgattr exists or question relates to CNHN_ZONE/cfgattr changes:
            • Parse cfgattr string using these rules:
              1) Split by "->" into two parts (before and after)
              2) Remove "interface[" prefix (if exists) and trailing "]" (if exists)
              3) Split each part by comma or whitespace, normalize and trim
              4) "Added" = values in new list but NOT in old list
              5) "Removed" = values in old list but NOT in new list
            • PARSING EXAMPLE:
              Input: "interface[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab->LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
              Step 1: Split by "->"
              - Before: "[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab"
              - After: "LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
              Step 2: Remove prefix "interface[" and trailing "]", then split by whitespace
              - Initial list: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV, CNHN_Wire_Lab
              - Final list: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV
              Step 3: Compare
              - Added: [] (none)
              - Removed: [CNHN_Wire_Lab]
            • OUTPUT FORMAT (timeline sorted by @timestamp asc):
              For each change event:
              - Time: [@timestamp in DD/MM/YYYY HH:mm:ss format]
              - User: [source.user.name]
              - Source IP: [source.ip]
              - Action: [message field content]
              - Initial config: [list before arrow]
              - Final config: [list after arrow]
              - Added interfaces: [difference - new values]
              - Removed interfaces: [difference - missing values]
              IMPORTANT: Always show both Initial and Final config, even if identical (no changes).
            • If no "->" in cfgattr: treat entire string as current configuration list
            • QUERY PATTERN for cfgattr changes: {"query":{"bool":{"filter":[{"term":{"source.user.name":"USERNAME"}},{"match":{"message":"CNHN_ZONE"}}]}},"sort":[{"@timestamp":"asc"}],"size":200}
            ═══════════════════════════════════════════════════════════════
            ✅ EXAMPLE OF PERFECT RESPONSE (follow this style):
            ═══════════════════════════════════════════════════════════════
            
            ```
            Trong 7 ngày qua (từ 07/11 đến 14/11/2025), tôi phát hiện **5 user** có lưu lượng truy cập 
            web cao nhất với tổng cộng **493.38 GB** từ **448,695 sessions**. Đây là phân tích chi tiết:
            
            **🏆 Top 3 Users - Phân tích so sánh:**
            
            1. **ToiLV** - User có bandwidth cao nhất
               - Lưu lượng: 140.93 GB (151,234,567,890 bytes) - chiếm 28.6 percent tổng traffic
               - Sessions: 77,090 lần (trung bình 11,013 sessions/ngày)
               - Websites truy cập: 95 domains
               - Đặc điểm: Lưu lượng/session cao (1.83 MB/session), tập trung vào streaming/download
               - Source IPs: 10.4.100.25, 10.4.100.112 (2 IPs chính)
               - Interface: CNHN_ZONE (outbound)
               - Top destinations: youtube.com (45 GB), cloudflare.com (28 GB), google.com (15 GB)
            
            2. **HungDT** - User đa dạng nhất
               - Lưu lượng: 138.20 GB (148,456,789,123 bytes) - sát nút ToiLV chỉ kém 2.7 GB
               - Sessions: 89,665 lần (cao hơn ToiLV 16.3 percent)
               - Websites truy cập: 377 domains - **gấp 4 lần ToiLV**, cho thấy browsing pattern rất đa dạng
               - Đặc điểm: Lưu lượng/session thấp hơn (1.54 MB/session), chủ yếu web browsing
               - Source IP: 10.4.100.87
               - Top activities: research, documentation, multiple SaaS platforms
            
            3. **LinhNTN** - User active nhất
               - Lưu lượng: 97.40 GB (104,634,289,152 bytes)
               - Sessions: **114,174 lần** - cao nhất trong top 5 (25.4 percent tổng sessions)
               - Websites: chỉ 52 domains - **thấp nhất** so với ToiLV và HungDT
               - Đặc điểm: Pattern tập trung cao (2,196 sessions/domain), có thể là automated tasks
               - Lưu lượng/session: 0.85 MB/session - **thấp nhất**, chủ yếu API calls
               - Source IP: 10.4.100.156
            
            **📊 So sánh các metrics quan trọng:**
            
            | Metric | ToiLV | HungDT | LinhNTN | Nhận xét |
            |--------|-------|--------|---------|----------|
            | Bandwidth | 140.93 GB | 138.20 GB | 97.40 GB | ToiLV dẫn đầu |
            | Sessions | 77,090 | 89,665 | 114,174 | LinhNTN nhiều nhất |
            | Websites | 95 | 377 | 52 | HungDT đa dạng x7 lần |
            | MB/session | 1.83 | 1.54 | 0.85 | ToiLV heavy usage |
            | Sessions/day | 11,013 | 12,809 | 16,311 | LinhNTN active nhất |
            
            **🔍 Insights từ dữ liệu:**
            - **ToiLV**: High-bandwidth user, likely streaming/media consumption (YouTube chiếm 32 percent traffic)
            - **HungDT**: Researcher/developer pattern - truy cập 377 sites khác nhau, đa dạng nhất
            - **LinhNTN**: Automated/scripted behavior - 114K sessions nhưng chỉ 52 sites, có thể bot/crawler
            - **Anomaly detected**: LinhNTN có ratio sessions/website = 2,196 (bình thường ~800-1000)
            
            **👥 Hai user còn lại:**
            - **NTDuong**: 59.43 GB (63,876,543,210 bytes), 89,295 sessions, 47 websites
            - **NTLinh**: 57.89 GB (62,178,321,456 bytes), 68,801 sessions, 48 websites
            
            **🎯 Tổng kết:**
            Traffic web tập trung vào nhóm 5 user này (493 GB) trong khi tổng traffic toàn công ty 
            là ~1.2 TB, nghĩa là 5 người chiếm **41 percent** bandwidth. ToiLV và HungDT cần monitor 
            bandwidth usage. LinhNTN cần verify có phải automated tasks hợp lệ.
            
            **Lý do chọn các trường:**
            - **source.user.name** (terms agg, size=50): Nhóm theo user để xác định top users cụ thể, 
              size 50 đủ lớn để bao phủ outliers nhưng không quá nhiều
            - **network.bytes** (sum aggregation): Tính tổng lưu lượng chính xác, field chuẩn ECS 
              cho bandwidth measurement
            - **network.direction = outbound**: Chỉ tính traffic đi ra (user requests), không tính 
              inbound để tránh đếm trùng
            - **@timestamp range (now-7d)**: 7 ngày đủ dài để thấy pattern, không quá ngắn (miss data) 
              hay quá dài (slow query)
            - **cardinality trên destination.as.organization.name**: Đếm unique websites, dùng ASN 
              thay vì domain để group CDN/cloud services chính xác hơn
            - **value_count trên @timestamp**: Đếm số sessions (mỗi hit = 1 session), simple và accurate
            - **order by total_bytes desc**: Sắp xếp theo bandwidth để tìm heavy users, không sort 
              theo sessions vì có thể có nhiều sessions nhưng ít data
            
            **Query đã sử dụng:**
            ```json
            {
                "size": 0,
                "query": {
                    "bool": {
                    "filter": [
                        {
                        "range": {
                            "@timestamp": {
                            "gte": "now-1d/d",
                            "lt": "now/d"
                            }
                        }
                        },
                        {
                        "terms": {
                            "observer.egress.interface.name": [
                            "sdwan",
                            "port1",
                            "port2",
                            "FTTH-WAN1-CMC",
                            "FTTH-WAN2-FPT"
                            ]
                        }
                        }
                    ]
                    }
                },
                "aggs": {
                    "top_users": {
                    "terms": {
                        "field": "source.user.name",
                        "size": 10,
                        "order": {
                        "total_bytes": "desc"
                        }
                    },
                    "aggs": {
                        "total_bytes": {
                        "sum": {
                            "field": "network.bytes"
                        }
                        },
                        "total_sessions": {
                        "value_count": {
                            "field": "@timestamp"
                        }
                        }
                    }
                    }
                }
            }
            ```
            ```
            
            ═══════════════════════════════════════════════════════════════
            ❌ BAD EXAMPLE (avoid this robotic style):
            ═══════════════════════════════════════════════════════════════
            
            ```
            Kết quả tìm kiếm:
            - Tổng số: 5 users
            - User 1: ToiLV
              * Sessions: 77,090
              * Bytes: 140,932,384,937
              * Websites: 95
            - User 2: HungDT
              * Sessions: 89,665
              * Bytes: 138,201,764,784
              * Websites: 377
            ...
            ```
            
            ═══════════════════════════════════════════════════════════════
            🚨 CRITICAL GUIDELINES
            ═══════════════════════════════════════════════════════════════
            
            MUST DO:
            ✅ Call searchElasticsearch tool first to get real data
            ✅ Write in natural, conversational Vietnamese (like talking to a colleague)
            ✅ Tell a story with the data - make it interesting and insightful
            ✅ ALWAYS convert bytes to GB/MB/KB with original value in parentheses
            ✅ Group similar logs (>5 identical patterns) - show "xN lần" instead of listing
            ✅ MUST include "Lý do chọn các trường" section (3-6 bullets explaining field choices)
            ✅ Use specific numbers and names from actual data
            ✅ End with "Query đã sử dụng:" in code block
            
            MUST NOT DO:
            ❌ Never make up data if tool returns empty
            ❌ Never use bullet lists without narrative context
            ❌ Never dump raw numbers without explanation or insights
            ❌ Never return only the query without analysis
            ❌ Never use robotic phrases like "Kết quả như sau:", "Danh sách:", "Tổng số:"
            ❌ Never show raw bytes (like 140932384937) - always convert to GB/MB/KB
            ❌ Never list >20 similar entries - group and summarize instead
            ❌ Never forget "Lý do chọn các trường" section
            
            ═══════════════════════════════════════════════════════════════
            📅 CONTEXT
            ═══════════════════════════════════════════════════════════════
            
            %s
            
            ═══════════════════════════════════════════════════════════════
            📋 ELASTICSEARCH SCHEMA INFORMATION
            ═══════════════════════════════════════════════════════════════
            
            Use these fields when building queries:
            
            SCHEMA INFORMATION:
            %s
            
            ROLE NORMALIZATION:
            %s
            
            FORTINET ACTION RULES:
            %s
            
            ═══════════════════════════════════════════════════════════════
            📚 EXAMPLE QUERIES FROM KNOWLEDGE BASE
            ═══════════════════════════════════════════════════════════════
            
            These are similar examples from knowledge base to help you:
            %s
                ═══════════════════════════════════════════════════════════════
                🚨 MANDATORY BUSINESS RULES (PRIORITY #0 - MUST FOLLOW)
               ═══════════════════════════════════════════════════════════════
                            You MUST apply specific filters based on keywords in the User Query.
                            Ignore any Dynamic Example above if it conflicts with these rules.
                            
                            1. IF QUERY CONTAINS: "internet", "ra ngoài", "outbound", "băng thông", "lưu lượng"
                               THEN YOU MUST ADD THESE FILTERS:

                               "terms": {
                                 "observer.egress.interface.name": ["sdwan", "port1", "port2", "FTTH-WAN1-CMC", "FTTH-WAN2-FPT"]
                               }
                            
                            2. IF QUERY CONTAINS: "truy cập", "sử dụng" (without specifying "internal")
                               -> Assume "outbound" internet traffic and apply the rules above.
                               
                            3. IF QUERY CONTAINS: "website", "trang web", "domain", "url"
                                   THEN YOU MUST ADD THIS FILTER:
                                   "exists": { "field": "url.domain" }
                            4. - Nếu query chứa cụm từ chính xác "sử dụng internet"
                                             → Không giới hạn giao thức(network.protocol) -> Thay vì filter {"terms": {"network.protocol": ["http", "https"]}}, bạn sẽ bỏ hẳn filter này.
            ═══════════════════════════════════════════════════════════════
            🚀 BEGIN NOW
            ═══════════════════════════════════════════════════════════════
            
            USER QUESTION: "%s"
            
            Now: Generate query → Call tool → Analyze data → Tell the story
            
            💡 Remember: Be conversational, insightful, and natural. Think like a data analyst 
            explaining findings to a colleague, not a robot listing results!
            """,
            dateContext,
            SchemaHint.getSchemaHint(),
            SchemaHint.getRoleNormalizationRules(),
            SchemaHint.getFortinetActionRules(),
            dynamicExamples,
            userQuery
        );

        System.out.println("[buildToolBasedPrompt] ✅ Prompt built - Length: " + prompt.length() + " chars");

        return prompt;
    }

    /**
     * Xử lý yêu cầu với PARALLEL PROCESSING - OpenAI và OpenRouter chạy đồng thời
     */
    public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String dateContext = generateDateContext();

        Map<String, Long> timingMetrics = new HashMap<>();
        long overallStartTime = System.currentTimeMillis();
        Map<String, Object> openaiResult = null;
        Map<String, Object> openrouterResult = null;

        try {
            System.out.println("[AiComparisonService] ===== BẮT ĐẦU CHẾ ĐỘ SO SÁNH VỚI PARALLEL PROCESSING =====");
            System.out.println("[AiComparisonService] Bắt đầu xử lý song song cho phiên: " + sessionId);
            System.out.println("[AiComparisonService] Tin nhắn người dùng: " + chatRequest.message());

            // --- BƯỚC 1: Chuẩn bị TOOL-BASED prompt (shared) ---
            String dynamicExamples = buildDynamicExamples(chatRequest.message());
            System.out.println("[AiComparisonService] 📚 Dynamic examples loaded: " +
                (dynamicExamples != null ? dynamicExamples.length() + " chars" : "NULL"));

            String userQueryForPrompt = chatRequest.message();
            if (userQueryForPrompt.toLowerCase().contains("admin") ||
                userQueryForPrompt.toLowerCase().contains("ad") ||
                userQueryForPrompt.toLowerCase().contains("administrator")) {
                userQueryForPrompt = userQueryForPrompt.replaceAll("(?i)\\badmin\\b", "Administrator")
                    .replaceAll("(?i)\\bad\\b", "Administrator")
                    .replaceAll("(?i)\\badministrator\\b", "Administrator");
            }

            // Build tool-based prompt
            String toolBasedPrompt = buildToolBasedPrompt(
                userQueryForPrompt,
                dateContext,
                dynamicExamples
            );

            System.out.println("[AiComparisonService] 🔧 Tool-based prompt created");
            System.out.println("[AiComparisonService] 📊 Prompt length: " + toolBasedPrompt.length() + " chars");
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📝 [TOOL-BASED PROMPT] Full System Prompt Being Used:");
            System.out.println("=".repeat(100));
            // System.out.println(toolBasedPrompt);
            System.out.println("=".repeat(100) + "\n");

            // --- BƯỚC 2: PARALLEL EXECUTION - OpenAI và OpenRouter đồng thời ---
            System.out.println("[AiComparisonService] 🚀 Bắt đầu xử lý SONG SONG OpenAI và OpenRouter...");
            System.out.println("[AiComparisonService] 🔧 Cả hai thread sẽ sử dụng tool 'searchElasticsearch'");

            // CompletableFuture cho OpenAI với tool enabled (sử dụng custom executor)
            CompletableFuture<Map<String, Object>> openaiFuture = CompletableFuture.supplyAsync(() ->
                processOpenAI(sessionId, chatRequest, toolBasedPrompt)
            );

            // CompletableFuture cho OpenRouter với tool enabled (sử dụng custom executor)
            CompletableFuture<Map<String, Object>> openrouterFuture = CompletableFuture.supplyAsync(() ->
                processOpenRouter(sessionId, chatRequest, toolBasedPrompt)
            );

            // Đợi cả hai hoàn thành
            System.out.println("[AiComparisonService] ⏳ Đang đợi cả OpenAI và OpenRouter hoàn thành...");
            CompletableFuture.allOf(openaiFuture, openrouterFuture).join();

            // Lấy kết quả
            try {
                openaiResult = openaiFuture.get();
            } catch (Exception e) {
                System.out.println("[AiComparisonService] ⚠️  OpenAI future error: " + e.getMessage());
                openaiResult = new HashMap<>();
                openaiResult.put("error", e.getMessage());
            }

            try {
                openrouterResult = openrouterFuture.get();
            } catch (Exception e) {
                System.out.println("[AiComparisonService] ⚠️  OpenRouter future error: " + e.getMessage());
                openrouterResult = new HashMap<>();
                openrouterResult.put("error", e.getMessage());
            }

            System.out.println("[AiComparisonService] ✅ CẢ HAI đã hoàn thành!");
            System.out.println("[AiComparisonService] 📊 OpenAI result keys: " + (openaiResult != null ? String.join(", ", openaiResult.keySet()) : "NULL"));
            System.out.println("[AiComparisonService] 📊 OpenRouter result keys: " + (openrouterResult != null ? String.join(", ", openrouterResult.keySet()) : "NULL"));

            // --- BƯỚC 3: Merge results ---
            long totalProcessingTime = System.currentTimeMillis() - overallStartTime;

            result.put("success", true);

            // Sử dụng HashMap thay vì Map.of() để tránh NullPointerException với giá trị null
            Map<String, Object> queryGeneration = new HashMap<>();
            if (openaiResult != null) {
                queryGeneration.put("openai", openaiResult.get("generation"));
            }
            if (openrouterResult != null) {
                queryGeneration.put("openrouter", openrouterResult.get("generation"));
            }
            result.put("query_generation_comparison", queryGeneration);

            Map<String, Object> elasticsearchComparison = new HashMap<>();
            if (openaiResult != null) {
                elasticsearchComparison.put("openai", openaiResult.get("elasticsearch"));
            }
            if (openrouterResult != null) {
                elasticsearchComparison.put("openrouter", openrouterResult.get("elasticsearch"));
            }
            result.put("elasticsearch_comparison", elasticsearchComparison);

            Map<String, Object> responseComparison = new HashMap<>();
            if (openaiResult != null) {
                responseComparison.put("openai", openaiResult.get("response"));
            }
            if (openrouterResult != null) {
                responseComparison.put("openrouter", openrouterResult.get("response"));
            }
            result.put("response_generation_comparison", responseComparison);

            // Timing metrics
            timingMetrics.put("total_processing_ms", totalProcessingTime);
            if (openaiResult != null && openaiResult.get("total_time_ms") != null) {
                timingMetrics.put("openai_total_ms", (Long) openaiResult.get("total_time_ms"));
            }
            if (openrouterResult != null && openrouterResult.get("total_time_ms") != null) {
                timingMetrics.put("openrouter_total_ms", (Long) openrouterResult.get("total_time_ms"));
            }
            if (openaiResult != null && openaiResult.get("search_time_ms") != null) {
                timingMetrics.put("openai_search_ms", (Long) openaiResult.get("search_time_ms"));
            }
            if (openrouterResult != null && openrouterResult.get("search_time_ms") != null) {
                timingMetrics.put("openrouter_search_ms", (Long) openrouterResult.get("search_time_ms"));
            }
            timingMetrics.put("parallel_execution", 1L); // 1 = true

            result.put("timing_metrics", timingMetrics);
            result.put("timestamp", now.toString());
            result.put("user_question", chatRequest.message());

            // Optimization stats
            Map<String, Object> optimizationStats = new HashMap<>();
            optimizationStats.put("parallel_processing", true);
            optimizationStats.put("threads_used", 2);
            optimizationStats.put("time_saved_vs_sequential_ms", calculateTimeSaved(openaiResult, openrouterResult, totalProcessingTime));
            result.put("optimization_stats", optimizationStats);

            System.out.println("[AiComparisonService] 🎉 So sánh PARALLEL hoàn thành!");
            System.out.println("[AiComparisonService] ⏱️ Tổng thời gian: " + totalProcessingTime + "ms");
            System.out.println("[AiComparisonService] 💾 Tiết kiệm: ~" +
                calculateTimeSaved(openaiResult, openrouterResult, totalProcessingTime) + "ms so với sequential");

            // Ghi log chi tiết thành công ra file
            Map<String, Object> successContext = new HashMap<>();
            successContext.put("sessionId", sessionId);
            successContext.put("userMessage", chatRequest.message());
            successContext.put("totalProcessingTimeMs", totalProcessingTime);
            successContext.put("timeSavedMs", calculateTimeSaved(openaiResult, openrouterResult, totalProcessingTime));

            // AI Summary
            Map<String, Object> aiSummary = new HashMap<>();
            if (openaiResult != null) {
                aiSummary.put("openai_totalMs", openaiResult.get("total_time_ms"));
                aiSummary.put("openai_searchMs", openaiResult.get("search_time_ms"));
                Object esObj = openaiResult.get("elasticsearch");
                if (esObj instanceof Map) {
                    aiSummary.put("openai_esSuccess", ((Map<String, Object>) esObj).get("success"));
                }
            }
            if (openrouterResult != null) {
                aiSummary.put("openrouter_totalMs", openrouterResult.get("total_time_ms"));
                aiSummary.put("openrouter_searchMs", openrouterResult.get("search_time_ms"));
                Object esObj = openrouterResult.get("elasticsearch");
                if (esObj instanceof Map) {
                    aiSummary.put("openrouter_esSuccess", ((Map<String, Object>) esObj).get("success"));
                }
            }
            successContext.put("aiSummary", aiSummary);

            // Lấy DSL queries từ cả hai AI để log
            String openaiDslQuery = "N/A";
            String openrouterDslQuery = "N/A";

            // Lấy OpenAI DSL query
            if (openaiResult != null) {
                Object esObj = openaiResult.get("elasticsearch");
                if (esObj instanceof Map) {
                    Object queryObj = ((Map<String, Object>) esObj).get("query");
                    if (queryObj != null) {
                        openaiDslQuery = queryObj.toString();
                    }
                }
            }

            // Lấy OpenRouter DSL query
            if (openrouterResult != null) {
                Object esObj = openrouterResult.get("elasticsearch");
                if (esObj instanceof Map) {
                    Object queryObj = ((Map<String, Object>) esObj).get("query");
                    if (queryObj != null) {
                        openrouterDslQuery = queryObj.toString();
                    }
                }
            }

            // Lưu DSL queries vào context để log
            successContext.put("openaiDslQuery", openaiDslQuery);
            successContext.put("openrouterDslQuery", openrouterDslQuery);

            // Lưu thêm dữ liệu đầy đủ theo từng nguồn để log riêng biệt
            try {
                if (openaiResult != null && openaiResult.get("elasticsearch") instanceof Map) {
                    Object od = ((Map<?, ?>) openaiResult.get("elasticsearch")).get("data");
                    if (od != null) successContext.put("openaiEsData", od.toString());
                }
            } catch (Exception ignore) {}
            try {
                if (openrouterResult != null && openrouterResult.get("elasticsearch") instanceof Map) {
                    Object rd = ((Map<?, ?>) openrouterResult.get("elasticsearch")).get("data");
                    if (rd != null) successContext.put("openrouterEsData", rd.toString());
                }
            } catch (Exception ignore) {}

            // Thêm dynamic examples vào log
            if (dynamicExamples != null && !dynamicExamples.isEmpty()) {
                successContext.put("dynamicExamples", dynamicExamples);
            }

            LogUtils.logDetailedSuccess(
                "AiComparisonService",
                String.format("Xử lý thành công yêu cầu song song OpenAI và OpenRouter (tiết kiệm %dms)", calculateTimeSaved(openaiResult, openrouterResult, totalProcessingTime)),
                successContext
            );

        } catch (Exception e) {
            long errorProcessingTime = System.currentTimeMillis() - overallStartTime;
            String errorMessage = "[AiComparisonService] ❌ Lỗi: " + e.getMessage();
            System.out.println(errorMessage);

            // Thu thập thông tin bối cảnh chi tiết
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("sessionId", sessionId);
            errorContext.put("userMessage", chatRequest.message());
            errorContext.put("processingTimeMs", errorProcessingTime);
            errorContext.put("timestamp", now.toString());
            errorContext.put("dateContext", dateContext);

            // Thêm thông tin về OpenAI và OpenRouter nếu có
            try {
                if (openaiResult != null) {
                    errorContext.put("openaiResult", openaiResult);
                }
            } catch (Exception ex) {
                errorContext.put("openaiResultError", ex.getMessage());
            }

            try {
                if (openrouterResult != null) {
                    errorContext.put("openrouterResult", openrouterResult);
                }
            } catch (Exception ex) {
                errorContext.put("openrouterResultError", ex.getMessage());
            }

            // Ghi log lỗi chi tiết ra file
            LogUtils.logDetailedError(
                "AiComparisonService",
                "Lỗi xử lý yêu cầu song song OpenAI và OpenRouter",
                e,
                errorContext
            );

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", now.toString());
            result.put("processing_time_ms", errorProcessingTime);
        }

        return result;
    }

    /**
     * Xử lý OpenAI trong thread riêng với TOOL-BASED approach
     */
    private Map<String, Object> processOpenAI(Long sessionId, ChatRequest chatRequest, String toolBasedPrompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            System.out.println("[OpenAI Thread] 🔵 Bắt đầu xử lý với TOOL searchElasticsearch...");
            System.out.println("[OpenAI Thread] 🔧 Tool enabled: searchElasticsearch");

            // Call AI with tool enabled (temperature 0.3 for OpenAI)
            ChatOptions chatOptions = ChatOptions.builder().temperature(0.3D).build();

            System.out.println("[OpenAI Thread] 🤖 Calling ChatClient với tools...");
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📤 [OpenAI Thread] Sending to AI:");
            System.out.println("=".repeat(80));
            System.out.println("🔧 System Prompt: " + (toolBasedPrompt.length() > 200 ? toolBasedPrompt.substring(0, 200) + "... (truncated, total: " + toolBasedPrompt.length() + " chars)" : toolBasedPrompt));
            System.out.println("👤 User Message: " + chatRequest.message());
            System.out.println("🌡️  Temperature: 0.3");
            System.out.println("🔧 Tools Enabled: searchElasticsearch");
            System.out.println("🆔 Conversation ID: " + sessionId + "_openai");
            System.out.println("=".repeat(80) + "\n");

            long aiStartTime = System.currentTimeMillis();

            // Retry logic cho rate limit errors
            String finalResponse = null;
            int maxRetries = 3;
            int retryCount = 0;

            while (retryCount <= maxRetries && finalResponse == null) {
                try {
                    finalResponse = chatClient
                        .prompt()
                        .system(toolBasedPrompt)
                        .user(chatRequest.message())
                        .options(chatOptions)
                        .tools(toolsConfig)  // ✅ ENABLE TOOL
                        .advisors(advisorSpec -> advisorSpec.param(
                            ChatMemory.CONVERSATION_ID, String.valueOf(sessionId) + "_openai"
                        ))
                        .call()
                        .content();
                } catch (NonTransientAiException e) {
                    // Kiểm tra nếu là rate limit error
                    if (e.getMessage() != null && e.getMessage().contains("Rate limit") && e.getMessage().contains("429")) {
                        long waitTimeMs = parseRateLimitWaitTime(e.getMessage());
                        if (waitTimeMs > 0 && retryCount < maxRetries) {
                            retryCount++;
                            System.out.println("[OpenAI Thread] ⚠️  Rate limit hit. Waiting " + waitTimeMs + "ms before retry " + retryCount + "/" + maxRetries);
                            try {
                                Thread.sleep(waitTimeMs + 100); // Thêm 100ms buffer
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                            }
                            continue; // Retry
                        } else {
                            System.out.println("[OpenAI Thread] ❌ Rate limit exceeded. Max retries reached or invalid wait time.");
                            throw e; // Re-throw nếu không thể retry
                        }
                    } else {
                        // Không phải rate limit, throw ngay
                        throw e;
                    }
                } catch (Exception e) {
                    // Kiểm tra nếu exception được wrap có chứa rate limit error
                    String errorMsg = e.getMessage();
                    Throwable cause = e.getCause();
                    while (cause != null && errorMsg != null && !errorMsg.contains("Rate limit")) {
                        errorMsg = cause.getMessage();
                        cause = cause.getCause();
                    }

                    if (errorMsg != null && errorMsg.contains("Rate limit") && errorMsg.contains("429")) {
                        long waitTimeMs = parseRateLimitWaitTime(errorMsg);
                        if (waitTimeMs > 0 && retryCount < maxRetries) {
                            retryCount++;
                            System.out.println("[OpenAI Thread] ⚠️  Rate limit hit (wrapped). Waiting " + waitTimeMs + "ms before retry " + retryCount + "/" + maxRetries);
                            try {
                                Thread.sleep(waitTimeMs + 100);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                            }
                            continue; // Retry
                        }
                    }
                    // Không phải rate limit hoặc không thể retry, throw ngay
                    throw e;
                }
            }

            if (finalResponse == null) {
                throw new RuntimeException("Failed to get AI response after " + maxRetries + " retries");
            }

            long aiEndTime = System.currentTimeMillis();

            System.out.println("[OpenAI Thread] ✅ AI response received");
            System.out.println("[OpenAI Thread] 📊 Response length: " + finalResponse.length() + " chars");

            // Extract query from response for logging
            String extractedQuery = extractQueryFromResponse(finalResponse);

            // Get metadata from tool result
            ToolsConfig.ToolResult toolResult = ToolsConfig.getToolResult();
            String esData = null;
            String esQuery = null;

            if (toolResult != null) {
                esData = toolResult.data;
                esQuery = toolResult.query != null ? toolResult.query : extractedQuery;
                System.out.println("[OpenAI Thread] 📊 Tool result - Data length: " + (esData != null ? esData.length() : 0) + " chars");
                System.out.println("[OpenAI Thread] 📊 Tool result - Data preview: " + (esData != null && esData.length() > 100 ? esData.substring(0, 100) + "..." : esData));
            } else {
                System.out.println("[OpenAI Thread] ⚠️ Tool result is NULL!");
                esQuery = extractedQuery;
            }

            // ✅ USE AI'S RESPONSE DIRECTLY - AI already formatted it after tool call
            String formattedResponse = finalResponse;

            // Clear ThreadLocal
            ToolsConfig.clearToolResult();

            System.out.println("[OpenAI Thread] 📦 Packaging results...");

            result.put("generation", Map.of(
                "response_time_ms", aiEndTime - aiStartTime,
                "model", ModelProvider.OPENAI.getModelName(),
                "query", esQuery != null ? esQuery : "Query embedded in tool call"
            ));

            // Determine success based on tool execution
            boolean esSuccess = toolResult != null && esData != null && !esData.trim().isEmpty();

            Map<String, Object> elasticsearchResult = new HashMap<>();
            // Lưu dữ liệu thực tế từ Elasticsearch để log chi tiết
            // Chỉ lưu dữ liệu thực tế nếu không phải error message
            if (esData != null && !esData.trim().isEmpty() &&
                !esData.startsWith("❌") && !esData.startsWith("⚠️") && !esData.startsWith("ℹ️")) {
                elasticsearchResult.put("data", esData);
            } else {
                elasticsearchResult.put("data", esData != null ? esData : "No data");
            }
            elasticsearchResult.put("success", esSuccess);
            elasticsearchResult.put("query", esQuery != null ? esQuery : "N/A");
            elasticsearchResult.put("tool_called", toolResult != null);
            result.put("elasticsearch", elasticsearchResult);

            result.put("search_time_ms", 0L);

            result.put("response", Map.of(
                "elasticsearch_query", esQuery != null ? esQuery : "N/A",
                "response", formattedResponse,
                "model", ModelProvider.OPENAI.getModelName(),
                "elasticsearch_data", "Processed by tool",
                "response_time_ms", aiEndTime - aiStartTime
            ));

            long totalTime = System.currentTimeMillis() - startTime;
            result.put("total_time_ms", totalTime);

            System.out.println("[OpenAI Thread] ✅ Hoàn thành trong " + totalTime + "ms");
            System.out.println("[OpenAI Thread] 📋 Result keys: " + String.join(", ", result.keySet()));

        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            System.err.println("[OpenAI Thread] ❌ Lỗi: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("sessionId", sessionId);
            errorContext.put("userMessage", chatRequest.message());
            errorContext.put("processingTimeMs", errorTime);
            errorContext.put("provider", "OpenAI");
            errorContext.put("modelName", ModelProvider.OPENAI.getModelName());
            errorContext.put("toolEnabled", true);

            LogUtils.logDetailedError(
                "AiComparisonService.OpenAI",
                "Lỗi xử lý yêu cầu OpenAI với tool",
                e,
                errorContext
            );

            result.put("error", e.getMessage());
            result.put("total_time_ms", errorTime);
        }

        return result;
    }

    /**
     * Xử lý OpenRouter trong thread riêng với TOOL-BASED approach
     */
    private Map<String, Object> processOpenRouter(Long sessionId, ChatRequest chatRequest, String toolBasedPrompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            System.out.println("[OpenRouter Thread] 🟠 Bắt đầu xử lý với TOOL searchElasticsearch...");
            System.out.println("[OpenRouter Thread] 🔧 Tool enabled: searchElasticsearch");

            // Call AI with tool enabled (temperature 0.7 for OpenRouter)
            ChatOptions chatOptions = ChatOptions.builder().temperature(0.7D).build();

            System.out.println("[OpenRouter Thread] 🤖 Calling ChatClient với tools...");
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📤 [OpenRouter Thread] Sending to AI:");
            System.out.println("=".repeat(80));
            System.out.println("🔧 System Prompt: " + (toolBasedPrompt.length() > 200 ? toolBasedPrompt.substring(0, 200) + "... (truncated, total: " + toolBasedPrompt.length() + " chars)" : toolBasedPrompt));
            System.out.println("👤 User Message: " + chatRequest.message());
            System.out.println("🌡️  Temperature: 0.7");
            System.out.println("🔧 Tools Enabled: searchElasticsearch");
            System.out.println("🆔 Conversation ID: " + sessionId + "_openrouter");
            System.out.println("=".repeat(80) + "\n");

            long aiStartTime = System.currentTimeMillis();

            // Retry logic cho rate limit errors
            String finalResponse = null;
            int maxRetries = 3;
            int retryCount = 0;

            while (retryCount <= maxRetries && finalResponse == null) {
                try {
                    finalResponse = chatClient
                        .prompt()
                        .system(toolBasedPrompt)
                        .user(chatRequest.message())
                        .options(chatOptions)
                        .tools(toolsConfig)  // ✅ ENABLE TOOL
                        .advisors(advisorSpec -> advisorSpec.param(
                            ChatMemory.CONVERSATION_ID, String.valueOf(sessionId) + "_openrouter"
                        ))
                        .call()
                        .content();
                } catch (NonTransientAiException e) {
                    // Kiểm tra nếu là rate limit error
                    if (e.getMessage() != null && e.getMessage().contains("Rate limit") && e.getMessage().contains("429")) {
                        long waitTimeMs = parseRateLimitWaitTime(e.getMessage());
                        if (waitTimeMs > 0 && retryCount < maxRetries) {
                            retryCount++;
                            System.out.println("[OpenRouter Thread] ⚠️  Rate limit hit. Waiting " + waitTimeMs + "ms before retry " + retryCount + "/" + maxRetries);
                            try {
                                Thread.sleep(waitTimeMs + 100); // Thêm 100ms buffer
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                            }
                            continue; // Retry
                        } else {
                            System.out.println("[OpenRouter Thread] ❌ Rate limit exceeded. Max retries reached or invalid wait time.");
                            throw e; // Re-throw nếu không thể retry
                        }
                    } else {
                        // Không phải rate limit, throw ngay
                        throw e;
                    }
                } catch (Exception e) {
                    // Kiểm tra nếu exception được wrap có chứa rate limit error
                    String errorMsg = e.getMessage();
                    Throwable cause = e.getCause();
                    while (cause != null && errorMsg != null && !errorMsg.contains("Rate limit")) {
                        errorMsg = cause.getMessage();
                        cause = cause.getCause();
                    }

                    if (errorMsg != null && errorMsg.contains("Rate limit") && errorMsg.contains("429")) {
                        long waitTimeMs = parseRateLimitWaitTime(errorMsg);
                        if (waitTimeMs > 0 && retryCount < maxRetries) {
                            retryCount++;
                            System.out.println("[OpenRouter Thread] ⚠️  Rate limit hit (wrapped). Waiting " + waitTimeMs + "ms before retry " + retryCount + "/" + maxRetries);
                            try {
                                Thread.sleep(waitTimeMs + 100);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                            }
                            continue; // Retry
                        }
                    }
                    // Không phải rate limit hoặc không thể retry, throw ngay
                    throw e;
                }
            }

            if (finalResponse == null) {
                throw new RuntimeException("Failed to get AI response after " + maxRetries + " retries");
            }

            long aiEndTime = System.currentTimeMillis();

            System.out.println("[OpenRouter Thread] ✅ AI response received");
            System.out.println("[OpenRouter Thread] 📊 Response length: " + finalResponse.length() + " chars");

            // Extract query from response for logging
            String extractedQuery = extractQueryFromResponse(finalResponse);

            // Get metadata from tool result
            ToolsConfig.ToolResult toolResult = ToolsConfig.getToolResult();
            String esData = null;
            String esQuery = null;

            if (toolResult != null) {
                esData = toolResult.data;
                esQuery = toolResult.query != null ? toolResult.query : extractedQuery;
                System.out.println("[OpenRouter Thread] 📊 Tool result - Data length: " + (esData != null ? esData.length() : 0) + " chars");
                System.out.println("[OpenRouter Thread] 📊 Tool result - Data preview: " + (esData != null && esData.length() > 100 ? esData.substring(0, 100) + "..." : esData));
            } else {
                System.out.println("[OpenRouter Thread] ⚠️ Tool result is NULL!");
                esQuery = extractedQuery;
            }

            // ✅ USE AI'S RESPONSE DIRECTLY - AI already formatted it after tool call
            String formattedResponse = finalResponse;

            // Clear ThreadLocal
            ToolsConfig.clearToolResult();

            System.out.println("[OpenRouter Thread] 📦 Packaging results...");

            result.put("generation", Map.of(
                "response_time_ms", aiEndTime - aiStartTime,
                "model", ModelProvider.OPENROUTER.getModelName(),
                "query", esQuery != null ? esQuery : "Query embedded in tool call"
            ));

            // Determine success based on tool execution
            boolean esSuccess = toolResult != null && esData != null && !esData.trim().isEmpty();

            Map<String, Object> elasticsearchResult = new HashMap<>();
            // Lưu dữ liệu thực tế từ Elasticsearch để log chi tiết
            // Chỉ lưu dữ liệu thực tế nếu không phải error message
            if (esData != null && !esData.trim().isEmpty() &&
                !esData.startsWith("❌") && !esData.startsWith("⚠️") && !esData.startsWith("ℹ️")) {
                elasticsearchResult.put("data", esData);
            } else {
                elasticsearchResult.put("data", esData != null ? esData : "No data");
            }
            elasticsearchResult.put("success", esSuccess);
            elasticsearchResult.put("query", esQuery != null ? esQuery : "N/A");
            elasticsearchResult.put("tool_called", toolResult != null);
            result.put("elasticsearch", elasticsearchResult);

            result.put("search_time_ms", 0L);

            result.put("response", Map.of(
                "elasticsearch_query", esQuery != null ? esQuery : "N/A",
                "response", formattedResponse,
                "model", ModelProvider.OPENROUTER.getModelName(),
                "elasticsearch_data", "Processed by tool",
                "response_time_ms", aiEndTime - aiStartTime
            ));

            long totalTime = System.currentTimeMillis() - startTime;
            result.put("total_time_ms", totalTime);

            System.out.println("[OpenRouter Thread] ✅ Hoàn thành trong " + totalTime + "ms");
            System.out.println("[OpenRouter Thread] 📋 Result keys: " + String.join(", ", result.keySet()));

        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            System.err.println("[OpenRouter Thread] ❌ Lỗi: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("sessionId", sessionId);
            errorContext.put("userMessage", chatRequest.message());
            errorContext.put("processingTimeMs", errorTime);
            errorContext.put("provider", "OpenRouter");
            errorContext.put("modelName", ModelProvider.OPENROUTER.getModelName());
            errorContext.put("toolEnabled", true);

            LogUtils.logDetailedError(
                "AiComparisonService.OpenRouter",
                "Lỗi xử lý yêu cầu OpenRouter với tool",
                e,
                errorContext
            );

            result.put("error", e.getMessage());
            result.put("total_time_ms", errorTime);
        }

        return result;
    }

    /**
     * Clean JSON response from AI
     */
    private String cleanJsonResponse(String raw) {
        System.out.println("[cleanJsonResponse] 🧹 Bắt đầu làm sạch JSON response...");

        if (raw == null) {
            System.out.println("[cleanJsonResponse] ⚠️  Input is NULL");
            return "";
        }

        System.out.println("[cleanJsonResponse] 📏 Original length: " + raw.length() + " chars");

        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            System.out.println("[cleanJsonResponse] 🔄 Loại bỏ ```json");
            clean = clean.substring(7);
        }
        if (clean.startsWith("```")) {
            System.out.println("[cleanJsonResponse] 🔄 Loại bỏ ```");
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            System.out.println("[cleanJsonResponse] 🔄 Loại bỏ ``` ở cuối");
            clean = clean.substring(0, clean.length() - 3);
        }

        String result = clean.trim();
        System.out.println("[cleanJsonResponse] ✅ Hoàn thành - Length: " + result.length() + " chars");

        return result;
    }

    /**
     * Extract Elasticsearch query from AI response (for logging purposes)
     */
    private String extractQueryFromResponse(String response) {
        if (response == null) {
            System.out.println("[extractQueryFromResponse] ⚠️  Response is NULL");
            return null;
        }

        try {
            System.out.println("[extractQueryFromResponse] 🔍 Bắt đầu trích xuất query từ response...");
            System.out.println("[extractQueryFromResponse] 📏 Response length: " + response.length());

            // Try to find JSON block in markdown
            int jsonStart = response.indexOf("```json");
            if (jsonStart >= 0) {
                System.out.println("[extractQueryFromResponse] ✅ Tìm thấy ```json block");
                int jsonEnd = response.indexOf("```", jsonStart + 7);
                if (jsonEnd > jsonStart) {
                    String query = response.substring(jsonStart + 7, jsonEnd).trim();
                    System.out.println("[extractQueryFromResponse] ✅ Trích xuất query thành công - Length: " + query.length());
                    return query;
                }
            }

            // Try to find any JSON-like structure
            int braceStart = response.indexOf("{");
            if (braceStart >= 0) {
                System.out.println("[extractQueryFromResponse] 🔍 Tìm thấy JSON object");
                // Find matching closing brace
                int depth = 0;
                for (int i = braceStart; i < response.length(); i++) {
                    char c = response.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            String query = response.substring(braceStart, i + 1);
                            System.out.println("[extractQueryFromResponse] ✅ Trích xuất JSON query thành công - Length: " + query.length());
                            return query;
                        }
                    }
                }
            }

            System.out.println("[extractQueryFromResponse] ⚠️  Không tìm thấy query trong response");
            return null;
        } catch (Exception e) {
            System.out.println("[extractQueryFromResponse] ❌ Lỗi: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tính thời gian tiết kiệm được nhờ parallel processing
     */
    private long calculateTimeSaved(Map<String, Object> openaiResult,
        Map<String, Object> openrouterResult,
        long actualTime) {
        long openaiTime = 0;
        long openrouterTime = 0;

        if (openaiResult != null && openaiResult.get("total_time_ms") != null) {
            openaiTime = ((Number) openaiResult.get("total_time_ms")).longValue();
        }

        if (openrouterResult != null && openrouterResult.get("total_time_ms") != null) {
            openrouterTime = ((Number) openrouterResult.get("total_time_ms")).longValue();
        }

        long sequentialTime = openaiTime + openrouterTime;
        long timeSaved = sequentialTime - actualTime;

        System.out.println("[calculateTimeSaved] ⏱️  OpenAI Time: " + openaiTime + "ms");
        System.out.println("[calculateTimeSaved] ⏱️  OpenRouter Time: " + openrouterTime + "ms");
        System.out.println("[calculateTimeSaved] 📊 Sequential Time: " + sequentialTime + "ms");
        System.out.println("[calculateTimeSaved] ⏱️  Actual Parallel Time: " + actualTime + "ms");
        System.out.println("[calculateTimeSaved] 💰 Time Saved: " + timeSaved + "ms (~" +
            (sequentialTime > 0 && timeSaved > 0 ? Math.round((double)timeSaved/sequentialTime*100) : 0) + "%)");

        return timeSaved;
    }

    /**
     * Build dynamic examples từ vector search
     */
    private String buildDynamicExamples(String userQuery) {
        System.out.println("[buildDynamicExamples] 🔍 Bắt đầu tìm ví dụ từ Vector Search...");
        System.out.println("[buildDynamicExamples] 📝 User Query: " + userQuery);

        String examples = vectorSearchService.findRelevantExamples(userQuery);

        System.out.println("[buildDynamicExamples] ✅ Hoàn thành tìm ví dụ");
        System.out.println("[buildDynamicExamples] 📊 Examples length: " + (examples != null ? examples.length() : 0) + " chars");

        return examples;
    }

    /**
     * Parse thời gian đợi từ rate limit error message
     * Format: "Please try again in X.XXXs"
     */
    private long parseRateLimitWaitTime(String errorMessage) {
        try {
            // Tìm pattern "Please try again in X.XXXs"
            int startIdx = errorMessage.indexOf("Please try again in ");
            if (startIdx >= 0) {
                int endIdx = errorMessage.indexOf("s", startIdx);
                if (endIdx > startIdx) {
                    String waitTimeStr = errorMessage.substring(startIdx + "Please try again in ".length(), endIdx).trim();
                    double waitTimeSeconds = Double.parseDouble(waitTimeStr);
                    long waitTimeMs = Math.round(waitTimeSeconds * 1000);
                    System.out.println("[parseRateLimitWaitTime] ⏱️  Parsed wait time: " + waitTimeSeconds + "s = " + waitTimeMs + "ms");
                    return waitTimeMs;
                }
            }
        } catch (Exception e) {
            System.out.println("[parseRateLimitWaitTime] ⚠️  Failed to parse wait time: " + e.getMessage());
        }
        // Default: đợi 2 giây nếu không parse được
        return 2000;
    }
}

