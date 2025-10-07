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
            You are an expert Elasticsearch query generator for Fortinet firewall logs. Generate ONE valid JSON query that matches user intent exactly.
            
            OUTPUT RULES
            - Return ONLY the JSON query object
            - No explanations, wrappers, or multiple queries
            - Valid JSON syntax required
            
            TIME HANDLING (Priority #1)
            Current Context: {dateContext}
            
            Relative Time (Preferred):
            - "5 phút qua/trước" → {"gte": "now-5m"}
            - "1 giờ qua/trước" → {"gte": "now-1h"}
            - "24 giờ qua/trước" → {"gte": "now-24h"}
            - "1 tuần qua/trước" → {"gte": "now-7d"}
            - "1 tháng qua/trước" → {"gte": "now-30d"}
            
            Specific Dates:
            - "hôm nay/today" → {"gte": "now/d"}
            - "hôm qua/yesterday" → {"gte": "now-1d/d"}
            - "ngày DD-MM" → {"gte": "YYYY-MM-DDT00:00:00.000+07:00", "lte": "YYYY-MM-DDT23:59:59.999+07:00"}
            
            SCHEMA INFORMATION
            {schemaInfo}
            
            ROLE NORMALIZATION RULES
            {roleNormalizationRules}
            USER QUERY: {userQuery}
            
            DYNAMIC EXAMPLES FROM KNOWLEDGE BASE
            {dynamic_examples}
            """;
    
    /**
     * Tạo prompt cho việc sinh truy vấn Elasticsearch với dynamic examples
     * 
     * @param userQuery Câu truy vấn của người dùng
     * @param dateContext Ngữ cảnh thời gian hiện tại
     * @param schemaInfo Thông tin schema
     * @param roleNormalizationRules Quy tắc chuẩn hóa vai trò
     * @param dynamicExamples Các ví dụ động từ knowledge base
     * @return Prompt đã được tạo với các placeholder đã được thay thế
     */
    public static String createQueryGenerationPrompt(String userQuery, String dateContext,
                                                    String schemaInfo, String roleNormalizationRules, String dynamicExamples) {
        Map<String, Object> params = new HashMap<>();
        params.put("userQuery", userQuery);
        params.put("dateContext", dateContext);
        params.put("schemaInfo", schemaInfo);
        params.put("roleNormalizationRules", roleNormalizationRules);
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
}
