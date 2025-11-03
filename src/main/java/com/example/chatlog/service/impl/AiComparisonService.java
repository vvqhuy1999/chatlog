package com.example.chatlog.service.impl;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private AiQueryService aiQueryService;
    
    @Autowired
    private AiResponseService aiResponseService;
    
    @Autowired
    private VectorSearchService vectorSearchService;
    
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
     * Xử lý yêu cầu với PARALLEL PROCESSING - OpenAI và OpenRouter chạy đồng thời
     */
    public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String dateContext = generateDateContext(now);
        
        Map<String, Long> timingMetrics = new HashMap<>();
        long overallStartTime = System.currentTimeMillis();
        Map<String, Object> openaiResult = null;
        Map<String, Object> openrouterResult = null;
        
        try {
            System.out.println("[AiComparisonService] ===== BẮT ĐẦU CHẾ ĐỘ SO SÁNH VỚI PARALLEL PROCESSING =====");
            System.out.println("[AiComparisonService] Bắt đầu xử lý song song cho phiên: " + sessionId);
            System.out.println("[AiComparisonService] Tin nhắn người dùng: " + chatRequest.message());
            
            // --- BƯỚC 1: Chuẩn bị prompt (shared) ---
            String fullSchema = SchemaHint.getSchemaHint();
            String dynamicExamples = buildDynamicExamples(chatRequest.message());
            
            String userQueryForPrompt = chatRequest.message();
            if (userQueryForPrompt.toLowerCase().contains("admin") || 
                userQueryForPrompt.toLowerCase().contains("administrator")) {
                userQueryForPrompt = userQueryForPrompt.replaceAll("(?i)\\badmin\\b", "Administrator")
                                                      .replaceAll("(?i)\\bad\\b", "Administrator")
                                                      .replaceAll("(?i)\\badministrator\\b", "Administrator");
            }
            
            String combinedPrompt = QueryPromptTemplate.createQueryGenerationPrompt(
                userQueryForPrompt,
                dateContext,
                fullSchema,
                SchemaHint.getRoleNormalizationRules(),
                SchemaHint.examplelog(),
                dynamicExamples
            );
            
            Prompt prompt = new Prompt(
                List.of(
                    new SystemMessage(combinedPrompt),
                    new UserMessage(chatRequest.message())
                )
            );

//            System.out.println("prompt: " + prompt);
            
            // --- BƯỚC 2: PARALLEL EXECUTION - OpenAI và OpenRouter đồng thời ---
            System.out.println("[AiComparisonService] 🚀 Bắt đầu xử lý SONG SONG OpenAI và OpenRouter...");
            
            // CompletableFuture cho OpenAI
            CompletableFuture<Map<String, Object>> openaiFuture = CompletableFuture.supplyAsync(() -> 
                processOpenAI(sessionId, chatRequest, prompt)
            );
            
            // CompletableFuture cho OpenRouter
            CompletableFuture<Map<String, Object>> openrouterFuture = CompletableFuture.supplyAsync(() -> 
                processOpenRouter(sessionId, chatRequest, prompt)
            );
            
            // Đợi cả hai hoàn thành
            System.out.println("[AiComparisonService] ⏳ Đang đợi cả OpenAI và OpenRouter hoàn thành...");
            CompletableFuture.allOf(openaiFuture, openrouterFuture).join();
            
            // Lấy kết quả
            openaiResult = openaiFuture.get();
            openrouterResult = openrouterFuture.get();
            
            System.out.println("[AiComparisonService] ✅ CẢ HAI đã hoàn thành!");
            
            // --- BƯỚC 3: Merge results ---
            long totalProcessingTime = System.currentTimeMillis() - overallStartTime;
            
            result.put("success", true);
            
            // Sử dụng HashMap thay vì Map.of() để tránh NullPointerException với giá trị null
            Map<String, Object> queryGeneration = new HashMap<>();
            queryGeneration.put("openai", openaiResult.get("generation"));
            queryGeneration.put("openrouter", openrouterResult.get("generation"));
            result.put("query_generation_comparison", queryGeneration);
            
            Map<String, Object> elasticsearchComparison = new HashMap<>();
            elasticsearchComparison.put("openai", openaiResult.get("elasticsearch"));
            elasticsearchComparison.put("openrouter", openrouterResult.get("elasticsearch"));
            result.put("elasticsearch_comparison", elasticsearchComparison);
            
            Map<String, Object> responseComparison = new HashMap<>();
            responseComparison.put("openai", openaiResult.get("response"));
            responseComparison.put("openrouter", openrouterResult.get("response"));
            result.put("response_generation_comparison", responseComparison);
            
            // Timing metrics
            timingMetrics.put("total_processing_ms", totalProcessingTime);
            timingMetrics.put("openai_total_ms", (Long) openaiResult.get("total_time_ms"));
            timingMetrics.put("openrouter_total_ms", (Long) openrouterResult.get("total_time_ms"));
            timingMetrics.put("openai_search_ms", (Long) openaiResult.get("search_time_ms"));
            timingMetrics.put("openrouter_search_ms", (Long) openrouterResult.get("search_time_ms"));
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
                aiSummary.put("openai_esSuccess", ((Map<String, Object>) openaiResult.get("elasticsearch")).get("success"));
            }
            if (openrouterResult != null) {
                aiSummary.put("openrouter_totalMs", openrouterResult.get("total_time_ms"));
                aiSummary.put("openrouter_searchMs", openrouterResult.get("search_time_ms"));
                aiSummary.put("openrouter_esSuccess", ((Map<String, Object>) openrouterResult.get("elasticsearch")).get("success"));
            }
            successContext.put("aiSummary", aiSummary);

            // ES Preview (kết hợp data từ cả hai, truncate nếu dài)
            StringBuilder esPreviewBuilder = new StringBuilder();

            // Xử lý OpenAI ES - luôn hiển thị label
            String openaiPreview = "(No OpenAI result)";
            if (openaiResult != null) {
                String openaiData = (String) ((Map<String, Object>) openaiResult.get("elasticsearch")).get("data");
                if (openaiData != null && openaiData.trim().length() > 0) {
                    openaiPreview = openaiData.length() > 500 ? openaiData.substring(0, 500) + "..." : openaiData;
                } else {
                    openaiPreview = "(Empty response)";
                }
            }
            esPreviewBuilder.append("OpenAI ES: ").append(openaiPreview);

            // Luôn thêm separator để phân biệt rõ ràng
            esPreviewBuilder.append(" | ");

            // Xử lý OpenRouter ES - luôn hiển thị label
            String openrouterPreview = "(No OpenRouter result)";
            if (openrouterResult != null) {
                String openrouterData = (String) ((Map<String, Object>) openrouterResult.get("elasticsearch")).get("data");
                if (openrouterData != null && openrouterData.trim().length() > 0) {
                    openrouterPreview = openrouterData.length() > 500 ? openrouterData.substring(0, 500) + "..." : openrouterData;
                } else {
                    openrouterPreview = "(Empty response)";
                }
            }
            esPreviewBuilder.append("OpenRouter ES: ").append(openrouterPreview);

            String esPreview = esPreviewBuilder.toString();
            if (esPreview.length() > 1000) {
                esPreview = esPreview.substring(0, 1000) + "...";
            }
            successContext.put("esPreview", esPreview);

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
     * Xử lý OpenAI trong thread riêng
     */
    private Map<String, Object> processOpenAI(Long sessionId, ChatRequest chatRequest, Prompt prompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("[OpenAI Thread] 🔵 Bắt đầu xử lý...");
            
            // Generate query
            ChatOptions chatOptions = ChatOptions.builder().temperature(0.0D).build();
            long queryStartTime = System.currentTimeMillis();
            
            String rawResponse = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .advisors(advisorSpec -> advisorSpec.param(
                    ChatMemory.CONVERSATION_ID, String.valueOf(sessionId)
                ))
                .call()
                .content();
            
            String cleanResponse = cleanJsonResponse(rawResponse);
            long queryEndTime = System.currentTimeMillis();
            
            System.out.println("[OpenAI Thread] 📝 DSL Query được OpenAI sinh ra:");
            System.out.println("=".repeat(80));
            System.out.println(cleanResponse);
            System.out.println("=".repeat(80));
            
            RequestBody queryBody = new RequestBody();
            queryBody.setQuery(1);
            queryBody.setBody(cleanResponse);
            
            result.put("generation", Map.of(
                "response_time_ms", queryEndTime - queryStartTime,
                "model", ModelProvider.OPENAI.getModelName(),
                "query", cleanResponse
            ));
            
            // Execute search
            System.out.println("[OpenAI Thread] 🔍 Đang thực thi query trên Elasticsearch...");
            long searchStartTime = System.currentTimeMillis();
            String[] searchResults = aiQueryService.getLogData(queryBody, chatRequest);
            long searchEndTime = System.currentTimeMillis();
            
            String content = searchResults != null && searchResults.length >= 1 ? searchResults[0] : "❌ No data";
            String finalQueryOpenAI = searchResults != null && searchResults.length >= 2 ? searchResults[1] : cleanResponse;
            
            System.out.println("[OpenAI Thread] 📊 Response từ Elasticsearch:");
            System.out.println("=".repeat(80));
            System.out.println("Final Query OpenAI: " + finalQueryOpenAI);
            System.out.println("-".repeat(80));
            System.out.println("OpenAI Data: " + content);
            System.out.println("=".repeat(80));
            
            result.put("elasticsearch", Map.of(
                "data", content,
                "success", !content.startsWith("❌"),
                "query", finalQueryOpenAI
            ));
            result.put("search_time_ms", searchEndTime - searchStartTime);
            
            // Generate response
            long responseStartTime = System.currentTimeMillis();
            String openaiResponse = aiResponseService.getAiResponseForComparison(
                sessionId + "_openai", chatRequest, content, finalQueryOpenAI
            );
//            System.out.println("openaiResponse: " + openaiResponse);
            long responseEndTime = System.currentTimeMillis();
            
            result.put("response", Map.of(
                "elasticsearch_query", finalQueryOpenAI,
                "response", openaiResponse,
                "model", ModelProvider.OPENAI.getModelName(),
                "elasticsearch_data", content,
                "response_time_ms", responseEndTime - responseStartTime
            ));
            
            long totalTime = System.currentTimeMillis() - startTime;
            result.put("total_time_ms", totalTime);
            
            System.out.println("[OpenAI Thread] ✅ Hoàn thành trong " + totalTime + "ms");
            
        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            String errorMessage = "[OpenAI Thread] ❌ Lỗi: " + e.getMessage();
            System.out.println(errorMessage);
            
            // Thu thập thông tin bối cảnh chi tiết
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("sessionId", sessionId);
            errorContext.put("userMessage", chatRequest.message());
            errorContext.put("processingTimeMs", errorTime);
            errorContext.put("provider", "OpenAI");
            errorContext.put("modelName", ModelProvider.OPENAI.getModelName());
            
            // Thêm thông tin về prompt nếu có
            try {
                errorContext.put("prompt", prompt.toString());
            } catch (Exception ex) {
                errorContext.put("promptError", ex.getMessage());
            }
            
            // Ghi log lỗi chi tiết ra file
            LogUtils.logDetailedError(
                "AiComparisonService.OpenAI", 
                "Lỗi xử lý yêu cầu OpenAI", 
                e, 
                errorContext
            );
            
            result.put("error", e.getMessage());
            result.put("total_time_ms", errorTime);
        }
        
        return result;
    }
    
    /**
     * Xử lý OpenRouter trong thread riêng
     */
    private Map<String, Object> processOpenRouter(Long sessionId, ChatRequest chatRequest, Prompt prompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("[OpenRouter Thread] 🟠 Bắt đầu xử lý...");
            
            // Generate query
            ChatOptions chatOptions = ChatOptions.builder().temperature(0.5D).build();
            long queryStartTime = System.currentTimeMillis();
            
            String rawResponse = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .advisors(advisorSpec -> advisorSpec.param(
                    ChatMemory.CONVERSATION_ID, String.valueOf(sessionId)
                ))
                .call()
                .content();
            
            String cleanResponse = cleanJsonResponse(rawResponse);
            long queryEndTime = System.currentTimeMillis();
            
            System.out.println("[OpenRouter Thread] 📝 DSL Query Openrouter được sinh ra:");
            System.out.println("=".repeat(80));
            System.out.println(cleanResponse);
            System.out.println("=".repeat(80));
            
            RequestBody queryBody = new RequestBody();
            queryBody.setQuery(1);
            queryBody.setBody(cleanResponse);
            
            result.put("generation", Map.of(
                "response_time_ms", queryEndTime - queryStartTime,
                "model", ModelProvider.OPENROUTER.getModelName(),
                "query", cleanResponse
            ));
            
            // Execute search
            System.out.println("[OpenRouter Thread] 🔍 Đang thực thi query trên Elasticsearch...");
            long searchStartTime = System.currentTimeMillis();
            String[] searchResults = aiQueryService.getLogData(queryBody, chatRequest);
            long searchEndTime = System.currentTimeMillis();
            
            String content = searchResults != null && searchResults.length >= 1 ? searchResults[0] : "❌ No data";
            String finalQueryOpenRouter = searchResults != null && searchResults.length >= 2 ? searchResults[1] : cleanResponse;
            
            System.out.println("[OpenRouter Thread] 📊 Response từ Elasticsearch:");
            System.out.println("=".repeat(80));
            System.out.println("Final Query OpenRouter: " + finalQueryOpenRouter);
            System.out.println("-".repeat(80));
            System.out.println("OpenRouter Data: " + content);
            System.out.println("=".repeat(80));
            
            result.put("elasticsearch", Map.of(
                "data", content,
                "success", !content.startsWith("❌"),
                "query", finalQueryOpenRouter
            ));
            result.put("search_time_ms", searchEndTime - searchStartTime);
            
            // Generate response
            long responseStartTime = System.currentTimeMillis();
            String openrouterResponse = aiResponseService.getAiResponseForComparison(
                sessionId + "_openrouter", chatRequest, content, finalQueryOpenRouter
            );
//          System.out.println("openrouterResponse: " + openrouterResponse);
            long responseEndTime = System.currentTimeMillis();
            
            result.put("response", Map.of(
                "elasticsearch_query", finalQueryOpenRouter,
                "response", openrouterResponse,
                "model", ModelProvider.OPENROUTER.getModelName(),
                "elasticsearch_data", content,
                "response_time_ms", responseEndTime - responseStartTime
            ));
            
            long totalTime = System.currentTimeMillis() - startTime;
            result.put("total_time_ms", totalTime);
            
            System.out.println("[OpenRouter Thread] ✅ Hoàn thành trong " + totalTime + "ms");
            
        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            String errorMessage = "[OpenRouter Thread] ❌ Lỗi: " + e.getMessage();
            System.out.println(errorMessage);
            
            // Thu thập thông tin bối cảnh chi tiết
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("sessionId", sessionId);
            errorContext.put("userMessage", chatRequest.message());
            errorContext.put("processingTimeMs", errorTime);
            errorContext.put("provider", "OpenRouter");
            errorContext.put("modelName", ModelProvider.OPENROUTER.getModelName());
            
            // Thêm thông tin về prompt nếu có
            try {
                errorContext.put("prompt", prompt.toString());
            } catch (Exception ex) {
                errorContext.put("promptError", ex.getMessage());
            }
            
            // Ghi log lỗi chi tiết ra file
            LogUtils.logDetailedError(
                "AiComparisonService.OpenRouter", 
                "Lỗi xử lý yêu cầu OpenRouter", 
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
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("```json")) clean = clean.substring(7);
        if (clean.startsWith("```")) clean = clean.substring(3);
        if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);
        return clean.trim();
    }
    
    /**
     * Tính thời gian tiết kiệm được nhờ parallel processing
     */
    private long calculateTimeSaved(Map<String, Object> openaiResult, 
                                     Map<String, Object> openrouterResult, 
                                     long actualTime) {
        long openaiTime = (Long) openaiResult.get("total_time_ms");
        long openrouterTime = (Long) openrouterResult.get("total_time_ms");
        long sequentialTime = openaiTime + openrouterTime;
        return sequentialTime - actualTime;
    }
    
    /**
     * Build dynamic examples từ vector search
     */
    private String buildDynamicExamples(String userQuery) {
        return vectorSearchService.findRelevantExamples(userQuery);
    }
}

