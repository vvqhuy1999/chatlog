package com.example.chatlog.utils;

import java.util.Map;
import java.util.HashMap;

/**
 * Lớp tiện ích để tạo prompt cho việc sinh truy vấn Elasticsearch
 * kết hợp các template query có sẵn từ QueryTemplates
 */
public class QueryPromptTemplate {
    
    /**
     * Template cho prompt sinh truy vấn Elasticsearch
     * Bao gồm các ví dụ từ QueryTemplates và hướng dẫn Chain-of-Thought
     */
    public static final String QUERY_GENERATION_TEMPLATE = """
            Elasticsearch Query Generator - Advanced System Prompt with CoT

            CORE OBJECTIVE
            You are an expert Elasticsearch DSL generator for Fortinet firewall security logs. Your task is to generate ONE valid, optimized JSON query that precisely matches the user's intent. Think step-by-step before outputting.

            THINKING PROCESS (Chain-of-Thought):
            1. ANALYZE INTENT: Determine if query needs filtering (bool), counting (aggs.value_count), aggregation (aggs.terms), or time-based (range).
            2. MAP FIELDS: Use exact field names from schema (e.g., source.ip, destination.ip, @timestamp, fortinet.firewall.action).
            3. BUILD STRUCTURE: Always include "query" for searches. Add "aggs" for counts/totals. Use "size":0 for aggregations-only.
            4. VALIDATE: Ensure JSON is valid, has required fields, and matches intent. No extra text.

            OUTPUT RULES (CRITICAL)
            - Return ONLY a valid JSON object (no markdown, no explanations, no arrays of queries)
            - JSON must contain at least "query" or "aggs" (or both)
            - Use double quotes for strings, proper escaping
            - For aggregations, set "size":0 to avoid fetching hits
            - If intent is unclear, default to broad search with aggregations

            TIME HANDLING (Priority #1)
            Current Context: {dateContext}

            Relative Time (Preferred - Use Elasticsearch native):
            - "5 phút qua/trước, 5 minutes ago" → {"gte": "now-5m"}
            - "1 giờ qua/trước, 1 hour ago" → {"gte": "now-1h"}
            - "24 giờ qua/trước, 24 hours ago" → {"gte": "now-24h"}
            - "1 tuần qua/trước, 1 week ago" → {"gte": "now-7d"}
            - "1 tháng qua/trước, 1 month ago" → {"gte": "now-30d"}

            Specific Dates (When exact dates mentioned):
            - "hôm nay, today" → {"gte": "now/d", "lte": "now/d"}
            - "hôm qua, yesterday" → {"gte": "now-1d/d", "lte": "now-1d/d"}
            - "ngày 15-09" → {"gte": "2024-09-15T00:00:00.000+07:00", "lte": "2024-09-15T23:59:59.999+07:00"}

            SCHEMA INFORMATION
            {schemaInfo}

            ROLE NORMALIZATION RULES
            {roleNormalizationRules}

            COMMON PATTERNS & EXAMPLES

            COUNT QUERIES (Use aggs.value_count or aggs.terms):
            - "đếm số log" → {"aggs": {"total_count": {"value_count": {"field": "_id"}}}}
            - "đếm log theo IP" → {"aggs": {"by_ip": {"terms": {"field": "source.ip", "size": 10}}}}

            FILTER QUERIES (Use bool with must/filter):
            - "log từ IP X" → {"query": {"bool": {"filter": [{"term": {"source.ip": "X"}}]}}}
            - "log attack" → {"query": {"bool": {"filter": [{"exists": {"field": "fortinet.firewall.attack"}}]}}}

            TIME + FILTER:
            - "log attack trong 1 giờ" → {"query": {"bool": {"filter": [{"range": {"@timestamp": {"gte": "now-1h"}}}, {"exists": {"field": "fortinet.firewall.attack"}}]}}}

            SPECIFIC PATTERNS:

            1. PORT SCAN DETECTION:
            {portScanDetection}

            2. BRUTE FORCE DETECTION:
            {bruteForceDetection}

            3. DATA EXFILTRATION DETECTION:
            {dataExfiltrationDetection}

            4. EXCESSIVE ADMIN PORT CONNECTIONS:
            {excessiveAdminPortConnections}

            5. BLOCKED TRAFFIC ANALYSIS:
            {topBlockedSourceIps}

            6. HIGH RISK IPS SESSIONS:
            {highRiskIpsSessions}

            7. OUTBOUND CONNECTIONS FROM VIETNAM:
            {outboundConnectionsFromVietnam}

            8. TOP BLOCKING RULES:
            {topBlockingRules}

            ERROR AVOIDANCE:
            - Never use text fields for exact matches (use keyword subfields if available)
            - Always check field existence before querying
            - For Vietnamese queries, translate intent to English field names
            - If query fails validation, simplify to basic term/range

            USER QUERY: {userQuery}

            Now, think step-by-step and output ONLY the JSON query.
            """;
    
    /**
     * Tạo prompt cho việc sinh truy vấn Elasticsearch
     * 
     * @param userQuery Câu truy vấn của người dùng
     * @param dateContext Ngữ cảnh thời gian hiện tại
     * @param roleNormalizationRules Quy tắc chuẩn hóa vai trò
     * @return Prompt đã được tạo với các placeholder đã được thay thế
     */
    public static String createQueryGenerationPrompt(String userQuery, String dateContext,
                                                    String schemaInfo, String roleNormalizationRules) {
        Map<String, Object> params = new HashMap<>();
        params.put("userQuery", userQuery);
        params.put("dateContext", dateContext);
        params.put("schemaInfo", schemaInfo);
        params.put("roleNormalizationRules", roleNormalizationRules);
        params.put("portScanDetection", QueryTemplates.PORT_SCAN_DETECTION);
        params.put("bruteForceDetection", QueryTemplates.BRUTE_FORCE_DETECTION);
        params.put("dataExfiltrationDetection", QueryTemplates.DATA_EXFILTRATION_DETECTION);
        params.put("excessiveAdminPortConnections", QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS);
        params.put("topBlockedSourceIps", QueryTemplates.TOP_BLOCKED_SOURCE_IPS);
        params.put("highRiskIpsSessions", QueryTemplates.HIGH_RISK_IPS_SESSIONS);
        params.put("outboundConnectionsFromVietnam", QueryTemplates.OUTBOUND_CONNECTIONS_FROM_VIETNAM);
        params.put("topBlockingRules", QueryTemplates.TOP_BLOCKING_RULES);
        
        return formatTemplate(QUERY_GENERATION_TEMPLATE, params);
    }

    /**
     * Overload: Tạo prompt kèm các ví dụ động từ QueryTemplates dựa trên dynamicInputs
     */
    public static String createQueryGenerationPrompt(
            String userQuery,
            String dateContext,
            String schemaInfo,
            String roleNormalizationRules,
            Map<String, Object> dynamicInputs
    ) {
        String base = createQueryGenerationPrompt(userQuery, dateContext, schemaInfo, roleNormalizationRules);

        if (dynamicInputs == null || dynamicInputs.isEmpty()) {
            return base;
        }

        StringBuilder dynamic = new StringBuilder();

        Object sourceIp = dynamicInputs.get("sourceIp");
        if (sourceIp instanceof String s && !s.isBlank()) {
            try { dynamic.append("\n- TOP DESTINATIONS BY BYTES (from source IP):\n").append(QueryTemplates.getTopDestinationsByBytesFromSource(s)).append('\n'); } catch (Exception ignored) {}
        }

        Object destinationIp = dynamicInputs.get("destinationIp");
        if (destinationIp instanceof String d && !d.isBlank()) {
            try { dynamic.append("\n- DNAT SESSIONS TO INTERNAL SERVER:\n").append(QueryTemplates.getDnatSessionsToInternalServer(d)).append('\n'); } catch (Exception ignored) {}
        }

        Object timeRange = dynamicInputs.get("timeRange");
        Object threshold = dynamicInputs.get("threshold");
        if (timeRange instanceof String tr && !tr.isBlank() && threshold instanceof Integer th) {
            try { dynamic.append("\n- CUSTOM PORT SCAN DETECTION:\n").append(QueryTemplates.createPortScanDetection(tr, th)).append('\n'); } catch (Exception ignored) {}
        }

        Object deviceName = dynamicInputs.get("deviceName");
        Object policyName = dynamicInputs.get("policyName");
        if (deviceName instanceof String dev && !dev.isBlank() && policyName instanceof String pol && !pol.isBlank()) {
            try { dynamic.append("\n- SHAPING POLICY DROPS (device/policy):\n").append(QueryTemplates.getDroppedTrafficByShapingPolicy(dev, pol)).append('\n'); } catch (Exception ignored) {}
        }

        Object countries = dynamicInputs.get("restrictedCountries");
        if (countries instanceof String[] arr && arr.length > 0) {
            try { dynamic.append("\n- CONNECTIONS TO RESTRICTED COUNTRIES:\n").append(QueryTemplates.getConnectionsToRestrictedCountries(arr)).append('\n'); } catch (Exception ignored) {}
        }

        if (dynamic.length() == 0) {
            return base;
        }
        return base + "\n\nDYNAMIC CONTEXT EXAMPLES:\n" + dynamic;
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
     * Tạo prompt bao gồm TẤT CẢ các template có sẵn trong QueryTemplates
     * và các ví dụ động nếu được cung cấp.
     */
    public static String createQueryGenerationPromptWithAllTemplates(
            String userQuery,
            String dateContext,
            String schemaInfo,
            String roleNormalizationRules,
            Map<String, Object> dynamicInputs
    ) {
        String base = createQueryGenerationPrompt(userQuery, dateContext, schemaInfo, roleNormalizationRules, dynamicInputs);

        StringBuilder library = new StringBuilder();
        library.append("\n\nALL TEMPLATE LIBRARY (STATIC):\n");

        // Liệt kê toàn bộ các template hằng (static final String)
        library.append("\n- PORT SCAN DETECTION:\n").append(QueryTemplates.PORT_SCAN_DETECTION).append('\n');
        library.append("\n- OUTBOUND PORT ANALYSIS:\n").append(QueryTemplates.OUTBOUND_PORT_ANALYSIS).append('\n');
        library.append("\n- BRUTE FORCE DETECTION:\n").append(QueryTemplates.BRUTE_FORCE_DETECTION).append('\n');
        library.append("\n- DATA EXFILTRATION DETECTION:\n").append(QueryTemplates.DATA_EXFILTRATION_DETECTION).append('\n');
        library.append("\n- EXCESSIVE ADMIN PORT CONNECTIONS:\n").append(QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS).append('\n');
        library.append("\n- TOP BLOCKED SOURCE IPS:\n").append(QueryTemplates.TOP_BLOCKED_SOURCE_IPS).append('\n');
        library.append("\n- HIGH RISK IPS SESSIONS:\n").append(QueryTemplates.HIGH_RISK_IPS_SESSIONS).append('\n');
        library.append("\n- OUTBOUND CONNECTIONS FROM VIETNAM:\n").append(QueryTemplates.OUTBOUND_CONNECTIONS_FROM_VIETNAM).append('\n');
        library.append("\n- TOP BLOCKING RULES:\n").append(QueryTemplates.TOP_BLOCKING_RULES).append('\n');
        library.append("\n- RDP TRAFFIC FROM WAN:\n").append(QueryTemplates.RDP_TRAFFIC_FROM_WAN).append('\n');
        library.append("\n- ABNORMAL ICMP TRAFFIC:\n").append(QueryTemplates.ABNORMAL_ICMP_TRAFFIC).append('\n');
        library.append("\n- ADMIN LOGINS:\n").append(QueryTemplates.ADMIN_LOGINS).append('\n');
        library.append("\n- FAILED ADMIN LOGINS:\n").append(QueryTemplates.FAILED_ADMIN_LOGINS).append('\n');
        library.append("\n- FIREWALL RULE CHANGES:\n").append(QueryTemplates.FIREWALL_RULE_CHANGES).append('\n');
        library.append("\n- CONFIGURATION CHANGES BY USER:\n").append(QueryTemplates.CONFIGURATION_CHANGES_BY_USER).append('\n');
        library.append("\n- ADMIN LOGINS FROM FOREIGN COUNTRIES:\n").append(QueryTemplates.ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES).append('\n');
        library.append("\n- IPS/AV CONFIGURATION CHANGES:\n").append(QueryTemplates.IPS_AV_CONFIGURATION_CHANGES).append('\n');
        library.append("\n- BRUTE FORCE LOGIN ATTEMPTS:\n").append(QueryTemplates.BRUTE_FORCE_LOGIN_ATTEMPTS).append('\n');
        library.append("\n- EXCESSIVE ADMIN PORT CONNECTIONS 15m:\n").append(QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS_QUERY).append('\n');
        library.append("\n- SHAPING POLICY CHANGES:\n").append(QueryTemplates.SHAPING_POLICY_CHANGES).append('\n');
        library.append("\n- WAN INTERFACE CHANGES:\n").append(QueryTemplates.WAN_INTERFACE_CHANGES).append('\n');
        library.append("\n- BLOCKED SSH CONNECTIONS BY USER:\n").append(QueryTemplates.BLOCKED_SSH_CONNECTIONS_BY_USER).append('\n');
        library.append("\n- BLOCKED RDP FROM LAN:\n").append(QueryTemplates.BLOCKED_RDP_FROM_LAN).append('\n');
        library.append("\n- BLOCKED ICMP BY USER:\n").append(QueryTemplates.BLOCKED_ICMP_BY_USER).append('\n');
        library.append("\n- PORT SCAN BY USER:\n").append(QueryTemplates.PORT_SCAN_BY_USER).append('\n');
        library.append("\n- BLOCKED FTP CONNECTIONS:\n").append(QueryTemplates.BLOCKED_FTP_CONNECTIONS).append('\n');
        library.append("\n- TOP USERS BLOCKED BY WEBFILTER:\n").append(QueryTemplates.TOP_USERS_BLOCKED_BY_WEBFILTER).append('\n');
        library.append("\n- LARGE DATA UPLOADS:\n").append(QueryTemplates.LARGE_DATA_UPLOADS).append('\n');
        library.append("\n- BLOCKED ADMIN WEB ACCESS FROM LAN:\n").append(QueryTemplates.BLOCKED_ADMIN_WEB_ACCESS_FROM_LAN).append('\n');
        library.append("\n- BLOCKED P2P TRAFFIC:\n").append(QueryTemplates.BLOCKED_P2P_TRAFFIC).append('\n');

        // Hướng dẫn gọi các hàm động còn lại (không có input thì không thể in trực tiếp)
        library.append("\nDYNAMIC TEMPLATE FUNCTIONS (CALL WITH INPUTS):\n");
        library.append("- formatQuery(template, params)\n");
        library.append("- createOutboundPortAnalysis(timeRange, size)\n");
        library.append("- createPortScanDetection(timeRange, threshold)\n");
        library.append("- getTopDestinationsByBytesFromSource(sourceIp)\n");
        library.append("- getFailedLoginsByUser(username)\n");
        library.append("- getDnatSessionsToInternalServer(destinationIp)\n");
        library.append("- getDroppedTrafficByShapingPolicy(deviceName, policyName)\n");
        library.append("- getConnectionsToRestrictedCountries(String[] restrictedCountries)\n");

        return base + library;
    }
}
