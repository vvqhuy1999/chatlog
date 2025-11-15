package com.example.chatlog.config;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.impl.AiQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Tool Configuration cho Parallel Execution
 * Định nghĩa tool searchElasticsearch để AI gọi trong quá trình parallel processing
 */
@Component
public class ToolsConfig {

    @Autowired
    private AiQueryService aiQueryService;
    
    // Thread-local storage để lưu data và query từ tool call
    private static final ThreadLocal<ToolResult> toolResultStorage = new ThreadLocal<>();
    
    /**
     * Inner class để lưu tool result
     */
    public static class ToolResult {
        public String data;
        public String query;
        
        public ToolResult(String data, String query) {
            this.data = data;
            this.query = query;
        }
    }
    
    /**
     * Lấy tool result từ thread-local storage
     */
    public static ToolResult getToolResult() {
        return toolResultStorage.get();
    }
    
    /**
     * Xóa tool result sau khi sử dụng
     */
    public static void clearToolResult() {
        toolResultStorage.remove();
    }

    /**
     * Tool để AI tự động sinh và thực thi Elasticsearch query
     * 
     * @param dslQuery Elasticsearch DSL query dưới dạng JSON string
     * @return Kết quả log từ Elasticsearch với metadata và context
     */
    @Tool(description = """
        Execute an Elasticsearch DSL query on Fortinet firewall logs.
        
        This tool executes your query and returns structured results.
        
        RETURNS one of:
        1. ✅ SUCCESS: Query results with data and metadata
        2. ℹ️ NO DATA: Query succeeded but 0 results found
        3. ❌ ERROR: Query failed with error details
        
        IMPORTANT - After receiving tool response:
        - For SUCCESS: Analyze the data and provide clear answer
        - For NO DATA: Explain why and suggest adjustments
        - For ERROR: Identify the issue and fix the query
        
        CRITICAL RULES:
        1. ALWAYS analyze the data returned
        2. NEVER just echo the query without analysis
        3. Provide DIRECT ANSWER to user's question first
        4. Include relevant statistics and findings
        5. End with the query used (in code block)
        
        Example good response:
        "Tìm thấy 1,234 logs trong 24h qua. Top source IP: 10.4.100.112 (856 logs).
        [detailed analysis...]
        **Query used:** ```json {...} ```"
        
        IMPORTANT:
        - ALWAYS call this tool to get real data
        - NEVER make up or assume data
        - If query fails, tool will return error message - fix and retry
        """)
    public String searchElasticsearch(String dslQuery) {
        long toolStartTime = System.currentTimeMillis();
        
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║ 🔧 [TOOL CALLED] searchElasticsearch                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("   📝 Thread: " + Thread.currentThread().getName());
        System.out.println("   📝 Timestamp: " + java.time.LocalDateTime.now());
        System.out.println("   📝 DSL Query received: " + (dslQuery != null ? dslQuery.length() + " chars" : "NULL"));
        
        // Validate input
        if (dslQuery == null || dslQuery.trim().isEmpty()) {
            long toolExecutionTime = System.currentTimeMillis() - toolStartTime;
            System.out.println("   ❌ DSL Query is NULL or empty. Aborting.");
            System.out.println("   ⏱️  Time: " + toolExecutionTime + "ms");
            return "❌ Error: DSL query is empty. Please generate a valid Elasticsearch query first.";
        }
        
        // Preview query
        String preview = dslQuery.length() > 150 ? dslQuery.substring(0, 150) + "..." : dslQuery;
        System.out.println("   📋 Query preview: " + preview);
        
        try {
            // Create mock ChatRequest (required by getLogData)
            ChatRequest mockChatRequest = new ChatRequest("Tool execution via " + Thread.currentThread().getName());
            
            // Create RequestBody with DSL
            RequestBody requestBody = new RequestBody();
            requestBody.setBody(dslQuery);
            requestBody.setQuery(1);
            
            System.out.println("   🔄 Calling aiQueryService.getLogData()...");
            
            // Execute query
            String[] results = aiQueryService.getLogData(requestBody, mockChatRequest);
            
            String logData = results != null && results.length >= 1 ? results[0] : "❌ No data";
            String actualQuery = results != null && results.length >= 2 ? results[1] : dslQuery;
            
            // ✅ VALIDATE DATA QUALITY
            boolean isError = logData.toLowerCase().startsWith("❌") || 
                             logData.toLowerCase().contains("error executing") ||
                             logData.toLowerCase().contains("elasticsearch error");
            
            boolean isEmpty = (logData.contains("\"hits\":[]") || logData.contains("\"hits\": []")) && 
                             !logData.contains("\"aggregations\"") &&
                             !logData.contains("\"aggs\"");
            
            // Detect "0 hits but has data" case
            boolean hasValidHits = logData.contains("\"hits\":{\"hits\":[") && 
                                  !logData.contains("\"hits\":{\"hits\":[]");
            
            boolean hasAggregations = logData.contains("\"aggregations\"") || 
                                     logData.contains("\"aggs\"");
            
            // Data is valid if has hits OR aggregations
            boolean hasValidData = hasValidHits || hasAggregations;
            
            // Lưu data và query vào ThreadLocal để sử dụng sau
            toolResultStorage.set(new ToolResult(logData, actualQuery));
            System.out.println("   💾 Saved tool result to ThreadLocal (data: " + logData.length() + " chars, query: " + actualQuery.length() + " chars)");
            
            long toolExecutionTime = System.currentTimeMillis() - toolStartTime;
            
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║ ✅ [TOOL SUCCESS] Query executed                          ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("   ✅ Status: SUCCESS");
            System.out.println("   📊 Result length: " + logData.length() + " chars");
            System.out.println("   📊 Has valid data: " + hasValidData);
            System.out.println("   📊 Has hits: " + hasValidHits);
            System.out.println("   📊 Has aggregations: " + hasAggregations);
            System.out.println("   📊 Is error: " + isError);
            System.out.println("   📊 Is empty: " + isEmpty);
            System.out.println("   ⏱️  Execution time: " + toolExecutionTime + "ms");
            
            // ✅ RETURN WITH CONTEXT AND GUIDANCE
            if (isError) {
                System.out.println("   ⚠️  Returning ERROR response to AI");
                return String.format("""
                    ❌ QUERY EXECUTION FAILED
                    
                    Error details:
                    %s
                    
                    ACTION REQUIRED:
                    - Review the error message above
                    - Fix the query syntax or field names
                    - Try again with corrected query
                    
                    Query that failed:
                    ```json
                    %s
                    ```
                    """, logData, actualQuery);
            }
            
            if (isEmpty) {
                System.out.println("   ℹ️  Returning NO DATA response to AI");
                return String.format("""
                    ℹ️ QUERY SUCCESSFUL - NO DATA FOUND
                    
                    The query executed successfully but returned 0 results.
                    
                    This means:
                    - Query syntax is correct
                    - No logs match the current filters
                    - Time range or filters may be too restrictive
                    
                    SUGGESTIONS:
                    - Expand time range (e.g., from 24h to 7d)
                    - Remove or relax some filters
                    - Check if field values are correct
                    
                    Query used:
                    ```json
                    %s
                    ```
                    """, actualQuery);
            }
            
            // ✅ SUCCESS WITH DATA
            System.out.println("   ✅ Returning SUCCESS response with valid data to AI");
            return String.format("""
                ✅ QUERY SUCCESSFUL - DATA RETRIEVED (Execution time: %dms)
                
                Raw Elasticsearch response:
                %s
                
                IMPORTANT INSTRUCTIONS:
                1. Parse the above JSON response
                2. Extract hits.hits array for log entries
                3. Extract aggregations if present for statistics
                4. Analyze the data and provide CLEAR ANSWER to user's question
                5. Include relevant numbers, IPs, users, patterns found
                6. End with the query used in markdown code block
                
                Query used:
                ```json
                %s
                ```
                
                Now analyze the data above and provide a comprehensive answer.
                """, toolExecutionTime, logData, actualQuery);
            
        } catch (Exception e) {
            long toolExecutionTime = System.currentTimeMillis() - toolStartTime;
            
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║ ❌ [TOOL ERROR] Query execution failed                     ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("   ❌ Status: ERROR");
            System.out.println("   💥 Error: " + e.getMessage());
            System.out.println("   ⏱️  Time before error: " + toolExecutionTime + "ms");
            System.out.println("   📍 Error type: " + e.getClass().getSimpleName());
            
            e.printStackTrace();
            
            // Return error message for AI to handle
            return "❌ Error executing Elasticsearch query: " + e.getMessage() + 
                   "\n\nPlease check:\n" +
                   "- JSON syntax is valid\n" +
                   "- Field names match schema\n" +
                   "- Bool clauses are arrays\n" +
                   "- 'aggs' and 'size' are at root level\n\n" +
                   "Fix the query and try again.";
        }
    }
}

