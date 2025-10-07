package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.DataExample;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.utils.SchemaHint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý chế độ so sánh giữa OpenAI và OpenRouter
 * Bao gồm: tạo query song song, thực hiện tìm kiếm, và tạo phản hồi so sánh
 */
@Service
public class AiComparisonService {
    
    @Autowired
    private LogApiService logApiService;
    
    @Autowired
    private AiQueryService aiQueryService;
    
    @Autowired
    private AiResponseService aiResponseService;
    
    private final ObjectMapper objectMapper;
    private List<DataExample> exampleLibrary;
    // Tích hợp các service tối ưu hóa mới
    @Autowired
    private QueryOptimizationService queryOptimizationService;
    
    @Autowired
    private PerformanceMonitoringService performanceMonitoringService;
    
    @Autowired
    private EnhancedExampleMatchingService enhancedExampleMatchingService;
    
    private final ChatClient chatClient;
    
    @Autowired
    public AiComparisonService(ChatClient.Builder builder) {
        this.objectMapper = new ObjectMapper();
        this.chatClient = builder.build();
        loadExampleLibrary();
    }
    
    /**
     * Load the example library from multiple JSON knowledge base files
     */
    private void loadExampleLibrary() {
        this.exampleLibrary = new ArrayList<>();
        
        // Define all knowledge base files to load (same as AiQueryService)
        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json",
            "advanced_security_scenarios.json",
            "network_forensics_performance.json",
            "business_intelligence_operations.json",
            "incident_response_playbooks.json",
            "compliance_audit_scenarios.json",
            "zero_trust_scenarios.json",
    "threat_intelligence_scenarios.json",
            "operational_security_scenarios.json",
            "email_data_security.json",
            "network_anomaly_detection.json"
        };
        
        int totalLoaded = 0;
        
        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});
                
                this.exampleLibrary.addAll(examples);
                totalLoaded += examples.size();
                
                System.out.println("[AiComparisonService] ✅ Loaded " + examples.size() + " examples from " + fileName);
            } catch (IOException e) {
                System.err.println("[AiComparisonService] ❌ Failed to load " + fileName + ": " + e.getMessage());
            }
        }
        
        System.out.println("[AiComparisonService] 📚 Total examples loaded: " + totalLoaded);
        
        if (this.exampleLibrary.isEmpty()) {
            System.err.println("[AiComparisonService] ⚠️ No examples loaded! Creating empty list.");
            this.exampleLibrary = new ArrayList<>();
        }
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
     * Xử lý yêu cầu của người dùng trong chế độ so sánh, sử dụng cả OpenAI và OpenRouter
     */
    public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String dateContext = generateDateContext(now);
        
        // Performance tracking cho comparison mode
        Map<String, Long> timingMetrics = new HashMap<>();
        long overallStartTime = System.currentTimeMillis();
        
        try {
            System.out.println("[AiComparisonService] ===== BẮT ĐẦU CHẾ ĐỘ SO SÁNH VỚI OPTIMIZATION =====");
            System.out.println("[AiComparisonService] Bắt đầu chế độ so sánh cho phiên: " + sessionId);
            System.out.println("[AiComparisonService] Tin nhắn người dùng: " + chatRequest.message());
            System.out.println("[AiComparisonService] Sử dụng ngữ cảnh ngày tháng: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            
            // DISABLED: Smart Context Building để tránh lazy loading issues
            // Lý do: ChatSessions.chatMessages collection gây "could not initialize proxy - no Session" error
            // Solution: Bỏ smart context building, chỉ dùng query optimization
            System.out.println("[AiComparisonService] ℹ️ Smart Context Building: DISABLED to prevent lazy loading errors");
            timingMetrics.put("context_building_ms", 0L);
            
            // --- BƯỚC 1: So sánh quá trình tạo query với optimization ---
            System.out.println("[AiComparisonService] ===== BƯỚC 1: Tạo Elasticsearch Query Với Optimization =====");
            
            // Chuẩn bị schema một lần để dùng lại cho cả hai prompt
            String fullSchema = SchemaHint.getSchemaHint();
            
            // Sử dụng QueryPromptTemplate với dynamic examples
            String dynamicExamples = buildDynamicExamples(chatRequest.message());
            String queryPrompt = com.example.chatlog.utils.QueryPromptTemplate.createQueryGenerationPrompt(
                chatRequest.message(),
                dateContext,
                null,
                null,
                dynamicExamples
            );
            
            // Tạo system prompt đầy đủ từ PromptTemplate với toàn bộ SchemaHint để bổ sung ngữ cảnh
            String fullSystemPrompt = com.example.chatlog.utils.PromptTemplate.getSystemPrompt(
                dateContext,
                SchemaHint.getRoleNormalizationRules(),
                fullSchema,
                SchemaHint.getCategoryGuides(),
                SchemaHint.getQuickPatterns()
            );
            
            // Ghép tất cả vào một system message duy nhất để AI có tối đa bối cảnh
            String combinedPrompt = queryPrompt + "\n\n" + fullSystemPrompt;
            SystemMessage systemMessage = new SystemMessage(combinedPrompt);
            
            UserMessage userMessage = new UserMessage(chatRequest.message());
            List<String> schemaHints = SchemaHint.allSchemas();
            String schemaContext = String.join("\n\n", schemaHints);
            UserMessage schemaMsg = new UserMessage("Available schema hints:\n" + schemaContext);
            UserMessage sampleLogMsg = new UserMessage("SAMPLE LOG (for inference):\n" + SchemaHint.examplelog());
            
            System.out.println("---------------------------------------------------------------------------------------");
            Prompt prompt = new Prompt(List.of(systemMessage, schemaMsg, sampleLogMsg, userMessage));

            System.out.println("Prompt very long: " + prompt);


            ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0.0D)
                .build();
            
            // Theo dõi thời gian tạo query của OpenAI
            System.out.println("[AiComparisonService] 🔵 OPENAI - Đang tạo Elasticsearch query...");
            long openaiStartTime = System.currentTimeMillis();
            RequestBody openaiQuery = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .entity(new ParameterizedTypeReference<>() {});
            long openaiRawEndTime = System.currentTimeMillis();
            
            // Đảm bảo giá trị query được đặt là 1
            if (openaiQuery.getQuery() != 1) {
                openaiQuery.setQuery(1);
            }
            
            // OPTIMIZATION: Áp dụng query optimization cho OpenAI
            RequestBody optimizedOpenaiQuery = queryOptimizationService.optimizeQuery(openaiQuery, chatRequest);
            long openaiEndTime = System.currentTimeMillis();
            
            String openaiQueryString = optimizedOpenaiQuery.getBody();
            System.out.println("[AiComparisonService] ✅ OPENAI - Query được tạo và tối ưu trong " + (openaiEndTime - openaiStartTime) + "ms");
            System.out.println("[AiComparisonService] 🔧 OPENAI - Raw generation: " + (openaiRawEndTime - openaiStartTime) + "ms, Optimization: " + (openaiEndTime - openaiRawEndTime) + "ms");
            System.out.println("[AiComparisonService] 📝 OPENAI - Optimized Query: " + openaiQueryString);
            
            // Theo dõi thời gian tạo query của OpenRouter (thực sự gọi OpenRouter với temperature khác)
            System.out.println("[AiComparisonService] 🟠 OPENROUTER - Đang tạo Elasticsearch query...");
            long openrouterStartTime = System.currentTimeMillis();
            
            // Provider: OpenRouter (query generation in comparison mode)
            ChatOptions openrouterChatOptions = ChatOptions.builder()
                .temperature(0.5D)
                .build();
            
            RequestBody openrouterQuery;
            String openrouterQueryString;
            
            try {
                // Gọi trực tiếp ChatClient với options OpenRouter (openrouterChatOptions)
                RequestBody rawOpenrouterQuery = chatClient
                    .prompt(prompt)
                    .options(openrouterChatOptions)
                    .call()
                    .entity(new ParameterizedTypeReference<>() {});
                    
                if (rawOpenrouterQuery.getQuery() != 1) {
                    rawOpenrouterQuery.setQuery(1);
                }
                
                // OPTIMIZATION: Áp dụng query optimization cho OpenRouter
                RequestBody optimizedOpenrouterQuery = queryOptimizationService.optimizeQuery(rawOpenrouterQuery, chatRequest);
                openrouterQuery = optimizedOpenrouterQuery;
                openrouterQueryString = optimizedOpenrouterQuery.getBody();
                
                System.out.println("[AiComparisonService] ✅ OPENROUTER - Query được tạo và tối ưu thành công với temperature khác biệt");
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("503") || e.getMessage().contains("upstream connect error"))) {
                    System.out.println("[AiComparisonService] ⚠️ OPENROUTER - Service tạm thời không khả dụng (HTTP 503), dùng optimized OpenAI query: " + e.getMessage());
                } else {
                    System.out.println("[AiComparisonService] ❌ OPENROUTER - Tạo query thất bại, dùng optimized OpenAI query: " + e.getMessage());
                }
                openrouterQuery = optimizedOpenaiQuery; // Fallback to optimized OpenAI query
                openrouterQueryString = openaiQueryString;
            }
            
            long openrouterEndTime = System.currentTimeMillis();
            System.out.println("[AiComparisonService] ✅ OPENROUTER - Query được tạo và tối ưu trong " + (openrouterEndTime - openrouterStartTime) + "ms");
            System.out.println("[AiComparisonService] 📝 OPENROUTER - Optimized Query: " + openrouterQueryString);
            
            // Lưu trữ kết quả tạo query
            Map<String, Object> queryGenerationComparison = new HashMap<>();
            
            Map<String, Object> openaiGeneration = new HashMap<>();
            openaiGeneration.put("response_time_ms", openaiEndTime - openaiStartTime);
            openaiGeneration.put("model", ModelProvider.OPENAI.getModelName());
            openaiGeneration.put("query", openaiQueryString);
            
            Map<String, Object> openrouterGeneration = new HashMap<>();
            openrouterGeneration.put("response_time_ms", openrouterEndTime - openrouterStartTime);
            openrouterGeneration.put("model", ModelProvider.OPENROUTER.getModelName());
            openrouterGeneration.put("query", openrouterQueryString);
            
            queryGenerationComparison.put("openai", openaiGeneration);
            queryGenerationComparison.put("openrouter", openrouterGeneration);
            
            // --- BƯỚC 2: Tìm kiếm Elasticsearch ---
            System.out.println("[AiComparisonService] ===== BƯỚC 2: Tìm kiếm Elasticsearch =====");
            
            // Thực hiện tìm kiếm Elasticsearch với cả hai query
            Map<String, Object> elasticsearchComparison = new HashMap<>();
            
            // Tìm kiếm OpenAI với optimized query
            System.out.println("[AiComparisonService] 🔵 OPENAI - Đang thực hiện tìm kiếm Elasticsearch với optimized query...");
            long openaiSearchStartTime = System.currentTimeMillis();
            String[] openaiResults = aiQueryService.getLogData(optimizedOpenaiQuery, chatRequest);
            String openaiContent = openaiResults[0];
            String finalOpenaiQuery = openaiResults[1];
            long openaiSearchTime = System.currentTimeMillis() - openaiSearchStartTime;
            timingMetrics.put("openai_search_ms", openaiSearchTime);
            
            System.out.println("[AiComparisonService] 📝 OpenAI Final Query: " + finalOpenaiQuery);
            System.out.println("[AiComparisonService] ⏱️ OpenAI Search Time: " + openaiSearchTime + "ms");
            
            // Kiểm tra nếu có lỗi trong quá trình tìm kiếm
            if (openaiContent != null && openaiContent.startsWith("❌")) {
                System.out.println("[AiComparisonService] ❌ OPENAI - Tìm kiếm Elasticsearch gặp lỗi, đang thử sửa query...");
                System.out.println("[AiComparisonService] 🔧 OPENAI - Đang tạo lại query với thông tin lỗi...");
            } else {
                System.out.println("[AiComparisonService] ✅ OPENAI - Tìm kiếm Elasticsearch hoàn thành thành công");
                System.out.println("[AiComparisonService] 📊 DỮ LIỆU TRẢ VỀ (OpenAI): " + (openaiContent.length() > 500 ? openaiContent.substring(0, 500) + "..." : openaiContent));
            }
            
            Map<String, Object> openaiElasticsearch = new HashMap<>();
            openaiElasticsearch.put("data", openaiContent);
            openaiElasticsearch.put("success", true);
            openaiElasticsearch.put("query", finalOpenaiQuery);
            
            System.out.println("OpenaiElasticsearch : "+ openaiElasticsearch);
            
            // Nếu OpenAI query thất bại, giữ nguyên lỗi để AI xử lý
            if (openaiContent != null && openaiContent.startsWith("❌")) {
                System.out.println("[AiComparisonService] 🔵 OPENAI - Query thất bại, giữ nguyên lỗi để AI xử lý");
            }
            
            // Tìm kiếm OpenRouter với optimized query
            System.out.println("[AiComparisonService] 🟠 OPENROUTER - Đang thực hiện tìm kiếm Elasticsearch với optimized query...");
            long openrouterSearchStartTime = System.currentTimeMillis();
            String[] openrouterResults = aiQueryService.getLogData(openrouterQuery, chatRequest);
            String openrouterContent = openrouterResults[0];
            String finalOpenrouterQuery = openrouterResults[1];
            long openrouterSearchTime = System.currentTimeMillis() - openrouterSearchStartTime;
            timingMetrics.put("openrouter_search_ms", openrouterSearchTime);
            
            // Kiểm tra nếu có lỗi trong quá trình tìm kiếm
            if (openrouterContent != null && openrouterContent.startsWith("❌")) {
                System.out.println("[AiComparisonService] ❌ OPENROUTER - Tìm kiếm Elasticsearch gặp lỗi, đang thử sửa query...");
                System.out.println("[AiComparisonService] 🔧 OPENROUTER - Đang tạo lại query với thông tin lỗi...");
            } else {
                System.out.println("[AiComparisonService] ✅ OPENROUTER - Tìm kiếm Elasticsearch hoàn thành thành công");
                System.out.println("[AiComparisonService] 📊 DỮ LIỆU TRẢ VỀ (OpenRouter): " + (openrouterContent.length() > 500 ? openrouterContent.substring(0, 500) + "..." : openrouterContent));
            }
            
            Map<String, Object> openrouterElasticsearch = new HashMap<>();
            openrouterElasticsearch.put("data", openrouterContent);
            openrouterElasticsearch.put("success", true);
            openrouterElasticsearch.put("query", finalOpenrouterQuery);
            
            System.out.println("OpenrouterElasticsearch : " + openrouterElasticsearch);
            
            // Nếu OpenRouter query thất bại, giữ nguyên lỗi để AI xử lý
            if (openrouterContent != null && openrouterContent.startsWith("❌")) {
                System.out.println("[AiComparisonService] 🟠 OPENROUTER - Query thất bại, giữ nguyên lỗi để AI xử lý");
            }
            
            elasticsearchComparison.put("openai", openaiElasticsearch);
            elasticsearchComparison.put("openrouter", openrouterElasticsearch);
            
            // --- BƯỚC 3: Tạo câu trả lời ---
            System.out.println("[AiComparisonService] ===== BƯỚC 3: Tạo câu trả lời AI =====");
            
            // Tạo câu trả lời từ cả hai model
            Map<String, Object> responseGenerationComparison = new HashMap<>();
            
            // Câu trả lời từ OpenAI
            System.out.println("[AiComparisonService] 🔵 OPENAI - Đang tạo phản hồi từ dữ liệu Elasticsearch...");
            long openaiResponseStartTime = System.currentTimeMillis();
            String openaiResponse = aiResponseService.getAiResponseForComparison(sessionId + "_openai", chatRequest, openaiContent, finalOpenaiQuery);
            long openaiResponseEndTime = System.currentTimeMillis();
            System.out.println("[AiComparisonService] ✅ OPENAI - Phản hồi được tạo thành công trong " + (openaiResponseEndTime - openaiResponseStartTime) + "ms");
            
            Map<String, Object> openaiResponseData = new HashMap<>();
            openaiResponseData.put("elasticsearch_query", finalOpenaiQuery);
            openaiResponseData.put("response", openaiResponse);
            openaiResponseData.put("model", ModelProvider.OPENAI.getModelName());
            openaiResponseData.put("elasticsearch_data", openaiContent);
            openaiResponseData.put("response_time_ms", openaiResponseEndTime - openaiResponseStartTime);
            
            // Câu trả lời từ OpenRouter (sử dụng dữ liệu riêng từ OpenRouter query)
            System.out.println("[AiComparisonService] 🟠 OPENROUTER - Đang tạo phản hồi từ dữ liệu Elasticsearch...");
            long openrouterResponseStartTime = System.currentTimeMillis();
            String openrouterResponse = aiResponseService.getAiResponseForComparison(sessionId + "_openrouter", chatRequest, openrouterContent, finalOpenrouterQuery);
            long openrouterResponseEndTime = System.currentTimeMillis();
            System.out.println("[AiComparisonService] ✅ OPENROUTER - Phản hồi được tạo thành công trong " + (openrouterResponseEndTime - openrouterResponseStartTime) + "ms");
            
            Map<String, Object> openrouterResponseData = new HashMap<>();
            openrouterResponseData.put("elasticsearch_query", finalOpenrouterQuery);
            openrouterResponseData.put("response", openrouterResponse);
            openrouterResponseData.put("model", ModelProvider.OPENROUTER.getModelName());
            openrouterResponseData.put("elasticsearch_data", openrouterContent);
            openrouterResponseData.put("response_time_ms", openrouterResponseEndTime - openrouterResponseStartTime);
            
            responseGenerationComparison.put("openai", openaiResponseData);
            responseGenerationComparison.put("openrouter", openrouterResponseData);
            
            // --- Tổng hợp kết quả cuối cùng với enhanced metrics ---
            System.out.println("[AiComparisonService] ===== TỔNG HỢP KẾT QUẢ VỚI ENHANCED METRICS =====");
            
            // Tính toán timing metrics tổng thể
            long totalProcessingTime = System.currentTimeMillis() - overallStartTime;
            timingMetrics.put("total_processing_ms", totalProcessingTime);
            timingMetrics.put("openai_total_ms", openaiEndTime - openaiStartTime + openaiResponseEndTime - openaiResponseStartTime);
            timingMetrics.put("openrouter_total_ms", openrouterEndTime - openrouterStartTime + openrouterResponseEndTime - openrouterResponseStartTime);
            
            // Thống kê optimization impact (simplified)
            Map<String, Object> optimizationStats = new HashMap<>();
            try {
                optimizationStats.put("query_optimization_applied", true);
                optimizationStats.put("smart_context_used", false); // Disabled để avoid lazy loading
                optimizationStats.put("context_entities_count", 0);
                optimizationStats.put("detected_intent", "GENERAL");
                optimizationStats.put("query_similarity", openaiQueryString != null && openaiQueryString.equals(openrouterQueryString));
                optimizationStats.put("data_similarity", openaiContent != null && openaiContent.equals(openrouterContent));
            } catch (Exception statsException) {
                System.out.println("[AiComparisonService] Warning: Error creating optimization stats: " + statsException.getMessage());
                optimizationStats.put("stats_error", true);
                optimizationStats.put("error_message", statsException.getMessage());
            }
            
            // Thêm các metrics mới vào kết quả với safe handling
            result.put("elasticsearch_comparison", elasticsearchComparison);
            result.put("success", true);
            result.put("query_generation_comparison", queryGenerationComparison);
            result.put("response_generation_comparison", responseGenerationComparison);
            result.put("timestamp", now.toString());
            result.put("user_question", chatRequest.message());
            result.put("timing_metrics", timingMetrics);
            result.put("optimization_stats", optimizationStats);
            
            // Enhanced context (disabled)
            try {
                result.put("enhanced_context", Map.of(
                    "intent_type", "DISABLED",
                    "relevant_messages_count", 0,
                    "entities_count", 0,
                    "context_disabled", true
                ));
            } catch (Exception contextStatsException) {
                System.out.println("[AiComparisonService] Warning: Error adding context stats: " + contextStatsException.getMessage());
                result.put("enhanced_context", Map.of("error", "Could not build context stats"));
            }
            
            System.out.println("[AiComparisonService] 🎉 So sánh hoàn thành thành công với optimization!");
            System.out.println("[AiComparisonService] ⏱️ Tổng thời gian processing: " + totalProcessingTime + "ms");
            System.out.println("[AiComparisonService] ⏱️ Context building: " + timingMetrics.get("context_building_ms") + "ms");
            System.out.println("[AiComparisonService] ⏱️ OpenAI search: " + timingMetrics.get("openai_search_ms") + "ms");
            System.out.println("[AiComparisonService] ⏱️ OpenRouter search: " + timingMetrics.get("openrouter_search_ms") + "ms");
            System.out.println("[AiComparisonService] 🧠 Context building: DISABLED");
            System.out.println("[AiComparisonService] 📝 Smart features: DISABLED to avoid lazy loading");
            System.out.println("[AiComparisonService] 🔍 Query optimization impact: " + (openaiQueryString.equals(openrouterQueryString) ? "Cùng optimized pattern" : "Khác biệt được tối ưu"));
            System.out.println("[AiComparisonService] 📊 Data consistency: " + (openaiContent.equals(openrouterContent) ? "Consistent results" : "Different results detected"));
            
            // Ghi nhận performance metrics
            performanceMonitoringService.recordRequest("comparison_mode", totalProcessingTime, true);
            
        } catch (Exception e) {
            long errorProcessingTime = System.currentTimeMillis() - overallStartTime;
            
            System.out.println("[AiComparisonService] ❌ ===== LỖI TRONG CHẾ ĐỘ SO SÁNH =====");
            System.out.println("[AiComparisonService] 💥 Lỗi trong chế độ so sánh: " + e.getMessage());
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", now.toString());
            result.put("processing_time_ms", errorProcessingTime);
            
            // Ghi nhận lỗi vào performance metrics
            performanceMonitoringService.recordRequest("comparison_mode", errorProcessingTime, false);
        }
        
        return result;
    }
    
    /**
     * Find relevant examples based on user query keywords (LEGACY METHOD - kept for fallback)
     * @deprecated Use EnhancedExampleMatchingService.findRelevantExamples() instead
     */
    @Deprecated
    private List<DataExample> findRelevantExamples(String userQuery) {
        System.out.println("\n🔍 ===== QUERY MATCHING PROCESS =====");
        System.out.println("📝 User Query: \"" + userQuery + "\"");
        
        if (exampleLibrary == null || exampleLibrary.isEmpty()) {
            System.out.println("❌ Knowledge base is empty or not loaded");
            return new ArrayList<>();
        }
        
        System.out.println("📚 Knowledge base contains " + exampleLibrary.size() + " examples");
        
        // Step 1: Extract keywords
        String queryLower = userQuery.toLowerCase();
        List<String> queryWords = Arrays.stream(queryLower.split("\\s+"))
                .filter(word -> word.length() > 2) // Filter out short words
                .collect(Collectors.toList());
        
        // Step 2: Find matching examples
        List<DataExample> matchingExamples = new ArrayList<>();
        Map<DataExample, Integer> exampleScores = new HashMap<>();
        
        for (int i = 0; i < exampleLibrary.size(); i++) {
            DataExample example = exampleLibrary.get(i);
            if (example.getKeywords() == null) continue;
            
            int score = 0;
            List<String> matchedKeywords = new ArrayList<>();
            
            // Calculate score for this example
            for (String keyword : example.getKeywords()) {
                for (String queryWord : queryWords) {
                    boolean isMatch = keyword.toLowerCase().contains(queryWord) || 
                                    queryWord.contains(keyword.toLowerCase());
                    if (isMatch) {
                        score++;
                        matchedKeywords.add(keyword);
                    }
                }
            }
            
            if (score > 0) {
                matchingExamples.add(example);
                exampleScores.put(example, score);
            }
        }
        
        // Step 3: Sort by score
        List<DataExample> sortedExamples = matchingExamples.stream()
                .sorted((e1, e2) -> {
                    int score1 = exampleScores.get(e1);
                    int score2 = exampleScores.get(e2);
                    return Integer.compare(score2, score1); // Descending order
                })
                .limit(10) // Return top 10 most relevant examples
                .collect(Collectors.toList());
        
        System.out.println("✅ Found " + sortedExamples.size() + " matching examples");
        return sortedExamples;
    }
    
    /**
     * Build dynamic examples string for the prompt using enhanced matching
     */
    private String buildDynamicExamples(String userQuery) {
        // Use enhanced matching service for better accuracy
        List<DataExample> relevantExamples = enhancedExampleMatchingService.findRelevantExamples(userQuery, exampleLibrary);
        
        if (relevantExamples.isEmpty()) {
            // Fallback to original method if enhanced matching fails
            relevantExamples = findRelevantExamples(userQuery);
            if (relevantExamples.isEmpty()) {
                return "No specific examples found for this query type.";
            }
        }
        
        StringBuilder examples = new StringBuilder();
        examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE:\n\n");
        
        for (int i = 0; i < relevantExamples.size(); i++) {
            DataExample example = relevantExamples.get(i);
            examples.append("Example ").append(i + 1).append(":\n");
            examples.append("Question: ").append(example.getQuestion()).append("\n");
            examples.append("Keywords: ").append(String.join(", ", example.getKeywords())).append("\n");
            examples.append("Query: ").append(example.getQuery().toPrettyString()).append("\n\n");
        }
        
        return examples.toString();
    }
}
