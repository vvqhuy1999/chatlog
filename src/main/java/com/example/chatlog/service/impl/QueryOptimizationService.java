package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.utils.SchemaHint;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service tối ưu hóa query Elasticsearch với machine learning patterns
 * Tăng độ chính xác thông qua query pattern recognition và optimization
 */
@Service
public class QueryOptimizationService {
    
    // Common query patterns với success rate cao
    private static final Map<String, String> PROVEN_PATTERNS = Map.of(
        "user_activity", "source.user.name",
        "ip_analysis", "source.ip or destination.ip", 
        "time_range", "now-Xh, now-Xd, now-Xm",
        "traffic_volume", "network.bytes.sum",
        "security_events", "event.category:security"
    );
    
    // Anti-patterns cần tránh
    private static final Set<String> AVOID_PATTERNS = Set.of(
        "nested.field.deep.access",  // Quá nhiều nested level
        "wildcard.*queries",         // Wildcard queries không hiệu quả
        "script.score.queries"       // Script queries chậm
    );

    /**
     * Tối ưu hóa query dựa trên patterns đã học
     */
    @Cacheable(value = "query_patterns", keyGenerator = "customKeyGenerator")
    public RequestBody optimizeQuery(RequestBody originalQuery, ChatRequest context) {
        String queryBody = originalQuery.getBody();
        
        // 1. Kiểm tra nếu user chỉ định size rõ ràng
        Integer explicitSize = detectExplicitSizeFromMessage(context.message());
        
        // 2. Phát hiện query pattern
        QueryPattern detectedPattern = detectQueryPattern(context.message());
        
        // 3. Áp dụng optimization rules
        String optimizedQuery = applyOptimizationRules(queryBody, detectedPattern);
        
        // 4. Áp dụng explicit size nếu có
        if (explicitSize != null) {
            System.out.println("[QueryOptimizationService] 🔢 User specified explicit size: " + explicitSize + " for pattern: " + detectedPattern);
            optimizedQuery = applyExplicitSize(optimizedQuery, explicitSize, detectedPattern);
        }
        
        // 5. Validate và fix common issues (bao gồm default size 50)
        optimizedQuery = validateAndFix(optimizedQuery);
        
        // Log size optimization results
        if (explicitSize != null) {
            System.out.println("[QueryOptimizationService] ✅ Applied explicit size: " + explicitSize);
        } else if (detectedPattern == QueryPattern.COUNTING) {
            System.out.println("[QueryOptimizationService] ✅ Applied aggregation size: 0 (counting query)");
        } else {
            System.out.println("[QueryOptimizationService] ✅ Applied default size: 50 (regular query)");
        }
        
        return new RequestBody(optimizedQuery, 1);
    }

    /**
     * Phát hiện pattern của query dựa trên intent analysis
     */
    private QueryPattern detectQueryPattern(String userMessage) {
        String msg = userMessage.toLowerCase();
        
        // Counting queries
        if (msg.contains("tổng") || msg.contains("đếm") || msg.contains("bao nhiêu")) {
            return QueryPattern.COUNTING;
        }
        
        // User analysis
        if (msg.contains("user") || msg.contains("người dùng") || msg.contains("tài khoản")) {
            return QueryPattern.USER_ANALYSIS;
        }
        
        // Traffic analysis  
        if (msg.contains("traffic") || msg.contains("lưu lượng") || msg.contains("bytes")) {
            return QueryPattern.TRAFFIC_ANALYSIS;
        }
        
        // Time-based queries
        if (msg.contains("phút") || msg.contains("giờ") || msg.contains("ngày")) {
            return QueryPattern.TIME_BASED;
        }
        
        // Security analysis
        if (msg.contains("blocked") || msg.contains("denied") || msg.contains("chặn")) {
            return QueryPattern.SECURITY_ANALYSIS;
        }
        
        return QueryPattern.GENERAL;
    }

    /**
     * Áp dụng optimization rules dựa trên pattern
     */
    private String applyOptimizationRules(String query, QueryPattern pattern) {
        switch (pattern) {
            case COUNTING:
                return optimizeCountingQuery(query);
            case USER_ANALYSIS:
                return optimizeUserQuery(query);
            case TRAFFIC_ANALYSIS:
                return optimizeTrafficQuery(query);
            case TIME_BASED:
                return optimizeTimeQuery(query);
            case SECURITY_ANALYSIS:
                return optimizeSecurityQuery(query);
            default:
                return query;
        }
    }

    private String optimizeCountingQuery(String query) {
        // Đảm bảo có size: 0 cho counting/aggregation queries (ghi đè default size 50)
        if (!query.contains("\"size\":") && query.contains("\"aggs\":")) {
            query = query.replaceFirst("\\{", "{\"size\": 0,");
        } else if (query.matches(".*\"size\"\\s*:\\s*50.*") && query.contains("\"aggs\":")) {
            // Nếu đã có size: 50 từ default nhưng đây là counting query, đổi thành size: 0
            query = query.replaceAll("\"size\"\\s*:\\s*50", "\"size\": 0");
        }
        
        // Thêm value_count aggregation nếu chưa có
        if (!query.contains("value_count") && query.contains("aggs")) {
            String aggPattern = "\"aggs\"\\s*:\\s*\\{";
            String replacement = "\"aggs\": {\"total_count\": {\"value_count\": {\"field\": \"@timestamp\"}},";
            query = query.replaceFirst(aggPattern, replacement);
        }
        
        return query;
    }

    private String optimizeUserQuery(String query) {
        // Keep original field names as per user requirement
        query = query.replaceAll("\"user\\.name\"", "\"source.user.name\"");
        // Note: source.user.name stays as is (no .keyword suffix)
        return query;
    }

    private String optimizeTrafficQuery(String query) {
        // Thêm sum aggregation cho network bytes
        if (query.contains("network.bytes") && !query.contains("sum")) {
            String aggPattern = "\"aggs\"\\s*:\\s*\\{";
            String replacement = "\"aggs\": {\"total_bytes\": {\"sum\": {\"field\": \"network.bytes\"}},";
            query = query.replaceFirst(aggPattern, replacement);
        }
        return query;
    }

    private String optimizeTimeQuery(String query) {
        // Prefer relative time expressions
        query = query.replaceAll("\"gte\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*?\"", 
                                 "\"gte\": \"now-24h\"");
        return query;
    }

    private String optimizeSecurityQuery(String query) {
        // Add security-specific filters
        if (!query.contains("event.category") && 
            (query.contains("blocked") || query.contains("denied"))) {
            // Add security category filter
            String filterPattern = "\"filter\"\\s*:\\s*\\[";
            String replacement = "\"filter\": [{\"term\": {\"event.category\": \"security\"}},";
            query = query.replaceFirst(filterPattern, replacement);
        }
        return query;
    }

    /**
     * Validate và fix common query issues
     */
    private String validateAndFix(String query) {
        // Fix common JSON syntax issues
        query = query.replaceAll(",\\s*}", "}");  // Remove trailing commas
        query = query.replaceAll(",\\s*]", "]");   // Remove trailing commas in arrays
        
        // Ensure proper quotes
        query = query.replaceAll("'", "\"");  // Convert single quotes to double quotes
        
        // Fix aggregation terms size issues
        query = fixAggregationTermsSize(query);
        
        // Thiết lập size mặc định là 50 nếu không có size và không phải aggregation query
        query = ensureDefaultSize(query);
        
        return query;
    }

    /**
     * Fix vấn đề "size": 0 trong aggregation terms
     * "size": 0 trong terms aggregation có nghĩa là không trả về bucket nào
     * Cần bỏ hoặc set size lớn để lấy tất cả terms
     */
    private String fixAggregationTermsSize(String query) {
        String originalQuery = query;
        
        // Approach: Remove any "size": 0 within terms aggregations
        // Find all terms blocks and remove size: 0 from them
        
        // Pattern for: "terms": { ... "size": 0 ... }
        // This is a more comprehensive approach using multiple passes
        
        // Pass 1: Remove "size": 0 that comes after field in terms
        query = query.replaceAll(
            "(\"terms\"\\s*:\\s*\\{[^}]*\"field\"\\s*:\\s*\"[^\"]*\"[^}]*),\\s*\"size\"\\s*:\\s*0([^}]*\\})", 
            "$1$2"
        );
        
        // Pass 2: Remove "size": 0 that comes before field in terms  
        query = query.replaceAll(
            "(\"terms\"\\s*:\\s*\\{[^}]*?)\"size\"\\s*:\\s*0\\s*,([^}]*\"field\"[^}]*\\})", 
            "$1$2"
        );
        
        // Pass 3: Handle case where size: 0 is the only property besides field
        query = query.replaceAll(
            "(\"terms\"\\s*:\\s*\\{)\\s*\"size\"\\s*:\\s*0\\s*,\\s*(\"field\"[^}]*\\})", 
            "$1$2"
        );
        
        // Pass 4: Handle reverse case  
        query = query.replaceAll(
            "(\"terms\"\\s*:\\s*\\{[^}]*\"field\"[^}]*),\\s*\"size\"\\s*:\\s*0\\s*(\\})", 
            "$1$2"
        );
        
        // Debug logging
        if (!originalQuery.equals(query)) {
            System.out.println("[QueryOptimizationService] ✅ Removed 'size: 0' from terms aggregations");
            // Show what was changed
            System.out.println("[QueryOptimizationService] Before: " + originalQuery.replaceAll("\\s+", " "));
            System.out.println("[QueryOptimizationService] After:  " + query.replaceAll("\\s+", " "));
        }
        
        return query;
    }

    /**
     * Đảm bảo size mặc định là 50 cho non-aggregation queries
     */
    private String ensureDefaultSize(String query) {
        // Kiểm tra nếu đã có size được chỉ định
        if (query.matches(".*\"size\"\\s*:\\s*\\d+.*")) {
            return query; // Đã có size, không cần thay đổi
        }
        
        // Kiểm tra nếu là aggregation query (thường cần size: 0)
        if (query.contains("\"aggs\"") || query.contains("\"aggregations\"")) {
            // Nếu là aggregation query mà chưa có size, set size = 0
            if (!query.matches(".*\"size\"\\s*:\\s*\\d+.*")) {
                query = query.replaceFirst("\\{", "{\"size\": 0,");
            }
            return query;
        }
        
        // Đối với non-aggregation queries, thiết lập size mặc định = 50
        query = query.replaceFirst("\\{", "{\"size\": 50,");
        
        return query;
    }

    /**
     * Kiểm tra nếu user có chỉ định số lượng kết quả rõ ràng trong message
     */
    private Integer detectExplicitSizeFromMessage(String userMessage) {
        String msg = userMessage.toLowerCase();
        
        // Pattern để tìm số lượng rõ ràng: "10 kết quả đầu", "top 5", "first 20", etc.
        java.util.regex.Pattern[] patterns = {
            java.util.regex.Pattern.compile("(\\d+)\\s*(kết quả|results?)"),
            java.util.regex.Pattern.compile("top\\s+(\\d+)"),
            java.util.regex.Pattern.compile("first\\s+(\\d+)"),
            java.util.regex.Pattern.compile("(\\d+)\\s*(đầu tiên|first)"),
            java.util.regex.Pattern.compile("hiển thị\\s+(\\d+)"),
            java.util.regex.Pattern.compile("lấy\\s+(\\d+)"),
            java.util.regex.Pattern.compile("show\\s+(\\d+)")
        };
        
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(msg);
            if (matcher.find()) {
                try {
                    int size = Integer.parseInt(matcher.group(1));
                    // Giới hạn size hợp lý (1-1000)
                    if (size >= 1 && size <= 1000) {
                        return size;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid numbers
                }
            }
        }
        
        return null; // Không tìm thấy size rõ ràng
    }

    /**
     * Áp dụng size được chỉ định rõ ràng từ user message
     */
    private String applyExplicitSize(String query, int explicitSize, QueryPattern pattern) {
        // Đối với counting queries, user vẫn có thể muốn giới hạn số documents trả về
        // nhưng vẫn cần aggregation
        if (pattern == QueryPattern.COUNTING && query.contains("\"aggs\"")) {
            // Giữ aggregation nhưng set size theo yêu cầu user
            if (query.matches(".*\"size\"\\s*:\\s*\\d+.*")) {
                query = query.replaceAll("\"size\"\\s*:\\s*\\d+", "\"size\": " + explicitSize);
            } else {
                query = query.replaceFirst("\\{", "{\"size\": " + explicitSize + ",");
            }
        } else {
            // Đối với non-aggregation queries, áp dụng size trực tiếp
            if (query.matches(".*\"size\"\\s*:\\s*\\d+.*")) {
                query = query.replaceAll("\"size\"\\s*:\\s*\\d+", "\"size\": " + explicitSize);
            } else {
                query = query.replaceFirst("\\{", "{\"size\": " + explicitSize + ",");
            }
        }
        
        return query;
    }

    /**
     * Phân tích performance của query patterns
     */
    public QueryPerformanceMetrics analyzeQueryPerformance(String query, long executionTime, boolean success) {
        QueryPattern pattern = detectQueryPatternFromQuery(query);
        return new QueryPerformanceMetrics(pattern, executionTime, success);
    }

    private QueryPattern detectQueryPatternFromQuery(String query) {
        if (query.contains("value_count") || query.contains("cardinality")) {
            return QueryPattern.COUNTING;
        }
        if (query.contains("source.user.name")) {
            return QueryPattern.USER_ANALYSIS;
        }
        if (query.contains("network.bytes") || query.contains("network.packets")) {
            return QueryPattern.TRAFFIC_ANALYSIS;
        }
        return QueryPattern.GENERAL;
    }

    // Enums và Data classes
    public enum QueryPattern {
        COUNTING, USER_ANALYSIS, TRAFFIC_ANALYSIS, TIME_BASED, SECURITY_ANALYSIS, GENERAL
    }

    public static class QueryPerformanceMetrics {
        public final QueryPattern pattern;
        public final long executionTimeMs;
        public final boolean success;
        
        public QueryPerformanceMetrics(QueryPattern pattern, long executionTimeMs, boolean success) {
            this.pattern = pattern;
            this.executionTimeMs = executionTimeMs;
            this.success = success;
        }
    }
}