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
    private final ChatClient chatClient;
    
    @Autowired
    public AiComparisonService(ChatClient.Builder builder) {
        this.objectMapper = new ObjectMapper();
        this.chatClient = builder.build();
        loadExampleLibrary();
    }
    
    /**
     * Load the example library from fortigate_queries_full.json
     */
    private void loadExampleLibrary() {
        try {
            ClassPathResource resource = new ClassPathResource("fortigate_queries_full.json");
            InputStream inputStream = resource.getInputStream();
            this.exampleLibrary = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});
            System.out.println("[AiComparisonService] ✅ Loaded " + exampleLibrary.size() + " examples from fortigate_queries_full.json");
        } catch (IOException e) {
            System.err.println("[AiComparisonService] ❌ Failed to load example library: " + e.getMessage());
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
        
        try {
            System.out.println("[AiComparisonService] ===== BẮT ĐẦU CHẾ ĐỘ SO SÁNH =====");
            System.out.println("[AiComparisonService] Bắt đầu chế độ so sánh cho phiên: " + sessionId);
            System.out.println("[AiComparisonService] Tin nhắn người dùng: " + chatRequest.message());
            System.out.println("[AiComparisonService] Sử dụng ngữ cảnh ngày tháng: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            
            // --- BƯỚC 1: So sánh quá trình tạo query ---
            System.out.println("[AiComparisonService] ===== BƯỚC 1: Tạo Elasticsearch Query =====");
            
            // Chuẩn bị schema một lần để dùng lại cho cả hai prompt
            String fullSchema = SchemaHint.getSchemaHint();
            
            // Sử dụng QueryPromptTemplate với dynamic examples
            String dynamicExamples = buildDynamicExamples(chatRequest.message());
            String queryPrompt = com.example.chatlog.utils.QueryPromptTemplate.createQueryGenerationPrompt(
                chatRequest.message(),
                dateContext,
                fullSchema,
                SchemaHint.getRoleNormalizationRules(),
                dynamicExamples
            );
            
            // Tạo system prompt đầy đủ từ PromptTemplate với toàn bộ SchemaHint để bổ sung ngữ cảnh
            String fullSystemPrompt = com.example.chatlog.utils.PromptTemplate.getSystemPrompt(
                dateContext,
                SchemaHint.getRoleNormalizationRules(),
                fullSchema,
                SchemaHint.getCategoryGuides(),
                SchemaHint.getNetworkTrafficExamples(),
                SchemaHint.getIPSSecurityExamples(),
                SchemaHint.getAdminRoleExample(),
                SchemaHint.getGeographicExamples(),
                SchemaHint.getFirewallRuleExamples(),
                SchemaHint.getCountingExamples(),
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
            long openaiEndTime = System.currentTimeMillis();
            
            // Đảm bảo giá trị query được đặt là 1
            if (openaiQuery.getQuery() != 1) {
                openaiQuery.setQuery(1);
            }
            
            String openaiQueryString = openaiQuery.getBody();
            System.out.println("[AiComparisonService] ✅ OPENAI - Query được tạo thành công trong " + (openaiEndTime - openaiStartTime) + "ms");
            System.out.println("[AiComparisonService] 📝 OPENAI - Query: " + openaiQueryString);
            
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
                openrouterQuery = chatClient
                    .prompt(prompt)
                    .options(openrouterChatOptions)
                    .call()
                    .entity(new ParameterizedTypeReference<>() {});
                    
                if (openrouterQuery.getQuery() != 1) {
                    openrouterQuery.setQuery(1);
                }
                openrouterQueryString = openrouterQuery.getBody();
                
                System.out.println("[AiComparisonService] ✅ OPENROUTER - Query được tạo thành công với temperature khác biệt");
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("503") || e.getMessage().contains("upstream connect error"))) {
                    System.out.println("[AiComparisonService] ⚠️ OPENROUTER - Service tạm thời không khả dụng (HTTP 503), dùng lại query OpenAI: " + e.getMessage());
                } else {
                    System.out.println("[AiComparisonService] ❌ OPENROUTER - Tạo query thất bại, dùng lại query OpenAI: " + e.getMessage());
                }
                openrouterQueryString = openaiQueryString; // Fallback to OpenAI query
            }
            
            long openrouterEndTime = System.currentTimeMillis();
            System.out.println("[AiComparisonService] ✅ OPENROUTER - Query được tạo trong " + (openrouterEndTime - openrouterStartTime) + "ms");
            System.out.println("[AiComparisonService] 📝 OPENROUTER - Query: " + openrouterQueryString);
            
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
            
            // Tìm kiếm OpenAI
            System.out.println("[AiComparisonService] 🔵 OPENAI - Đang thực hiện tìm kiếm Elasticsearch...");
            String[] openaiResults = aiQueryService.getLogData(openaiQuery, chatRequest);
            String openaiContent = openaiResults[0];
            String finalOpenaiQuery = openaiResults[1];
            System.out.println(finalOpenaiQuery);
            
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
            
            // Tìm kiếm OpenRouter (sử dụng query riêng từ OpenRouter)
            System.out.println("[AiComparisonService] 🟠 OPENROUTER - Đang thực hiện tìm kiếm Elasticsearch...");
            RequestBody openrouterRequestBody = new RequestBody(openrouterQueryString, 1);
            String[] openrouterResults = aiQueryService.getLogData(openrouterRequestBody, chatRequest);
            String openrouterContent = openrouterResults[0];
            String finalOpenrouterQuery = openrouterResults[1];
            
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
            
            // --- Tổng hợp kết quả cuối cùng ---
            System.out.println("[AiComparisonService] ===== TỔNG HỢP KẾT QUẢ =====");
            
            result.put("elasticsearch_comparison", elasticsearchComparison);
            result.put("success", true);
            result.put("query_generation_comparison", queryGenerationComparison);
            result.put("response_generation_comparison", responseGenerationComparison);
            result.put("timestamp", now.toString());
            result.put("user_question", chatRequest.message());
            
            System.out.println("[AiComparisonService] 🎉 So sánh hoàn thành thành công!");
            System.out.println("[AiComparisonService] ⏱️ Tổng thời gian OpenAI: " + (openaiEndTime - openaiStartTime + openaiResponseEndTime - openaiResponseStartTime) + "ms");
            System.out.println("[AiComparisonService] ⏱️ Tổng thời gian OpenRouter: " + (openrouterEndTime - openrouterStartTime + openrouterResponseEndTime - openrouterResponseStartTime) + "ms");
            System.out.println("[AiComparisonService] 🔍 Sự khác biệt query OpenAI vs OpenRouter: " + (!openaiQueryString.equals(openrouterQueryString) ? "Các query khác nhau được tạo" : "Cùng query được tạo"));
            System.out.println("[AiComparisonService] 📊 Sự khác biệt dữ liệu OpenAI vs OpenRouter: " + (!openaiContent.equals(openrouterContent) ? "Dữ liệu khác nhau được truy xuất" : "Cùng dữ liệu được truy xuất"));
            
        } catch (Exception e) {
            System.out.println("[AiComparisonService] ❌ ===== LỖI TRONG CHẾ ĐỘ SO SÁNH =====");
            System.out.println("[AiComparisonService] 💥 Lỗi trong chế độ so sánh: " + e.getMessage());
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", now.toString());
        }
        
        return result;
    }
    
    /**
     * Find relevant examples based on user query keywords
     */
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
        
        System.out.println("🔤 Step 1 - Extracted keywords: " + queryWords);
        
        // Step 2: Find matching examples with detailed logging
        List<DataExample> matchingExamples = new ArrayList<>();
        Map<DataExample, Integer> exampleScores = new HashMap<>();
        
        System.out.println("\n🔍 Step 2 - Searching through knowledge base:");
        for (int i = 0; i < exampleLibrary.size(); i++) {
            DataExample example = exampleLibrary.get(i);
            if (example.getKeywords() == null) continue;
            
            int score = 0;
            List<String> matchedKeywords = new ArrayList<>();
            
            System.out.printf("  📋 Example %d: %s\n", i + 1, 
                example.getQuestion().substring(0, Math.min(60, example.getQuestion().length())) + "...");
            System.out.printf("     Keywords: %s\n", String.join(", ", example.getKeywords()));
            
            // Calculate score for this example
            for (String keyword : example.getKeywords()) {
                for (String queryWord : queryWords) {
                    boolean isMatch = keyword.toLowerCase().contains(queryWord) || 
                                    queryWord.contains(keyword.toLowerCase());
                    if (isMatch) {
                        score++;
                        matchedKeywords.add(keyword);
                        System.out.printf("     ✅ Match: '%s' ↔ '%s'\n", queryWord, keyword);
                    }
                }
            }
            
            if (score > 0) {
                matchingExamples.add(example);
                exampleScores.put(example, score);
                System.out.printf("     🎯 Total Score: %d | Matched: %s\n", 
                    score, String.join(", ", matchedKeywords));
            } else {
                System.out.printf("     ❌ No matches found\n");
            }
            System.out.println();
        }
        
        System.out.println("📊 Step 3 - Sorting by relevance score:");
        
        // Step 3: Sort by score
        List<DataExample> sortedExamples = matchingExamples.stream()
                .sorted((e1, e2) -> {
                    int score1 = exampleScores.get(e1);
                    int score2 = exampleScores.get(e2);
//                    System.out.printf("  🔄 Comparing: Score %d vs %d\n", score1, score2);
                    return Integer.compare(score2, score1); // Descending order
                })
                .limit(5) // Return top 5 most relevant examples
                .collect(Collectors.toList());
        
        System.out.println("\n🎯 Step 4 - Final Results (Top " + sortedExamples.size() + "):");
        for (int i = 0; i < sortedExamples.size(); i++) {
            DataExample example = sortedExamples.get(i);
            int score = exampleScores.get(example);
            System.out.printf("  %d. Score: %d | %s\n", 
                i + 1, score, example.getQuestion());
            System.out.printf("     Keywords: %s\n", 
                String.join(", ", example.getKeywords()));
        }
        
        System.out.println("✅ Query matching process completed\n");
        return sortedExamples;
    }
    
    /**
     * Build dynamic examples string for the prompt
     */
    private String buildDynamicExamples(String userQuery) {
        System.out.println("\n📝 ===== BUILDING DYNAMIC EXAMPLES =====");
        System.out.println("🔍 Finding relevant examples for: \"" + userQuery + "\"");
        
        List<DataExample> relevantExamples = findRelevantExamples(userQuery);
        
        if (relevantExamples.isEmpty()) {
            System.out.println("⚠️ No relevant examples found, using fallback message");
            return "No specific examples found for this query type.";
        }
        
        System.out.println("🔨 Building dynamic examples string for AI prompt:");
        System.out.println("   - Found " + relevantExamples.size() + " relevant examples");
        
        StringBuilder examples = new StringBuilder();
        examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE:\n\n");
        
        for (int i = 0; i < relevantExamples.size(); i++) {
            DataExample example = relevantExamples.get(i);
            System.out.printf("   📄 Adding Example %d: %s\n", 
                i + 1, example.getQuestion().substring(0, Math.min(50, example.getQuestion().length())) + "...");
            System.out.printf("      Keywords: %s\n", String.join(", ", example.getKeywords()));
            
            examples.append("Example ").append(i + 1).append(":\n");
            examples.append("Question: ").append(example.getQuestion()).append("\n");
            examples.append("Keywords: ").append(String.join(", ", example.getKeywords())).append("\n");
            examples.append("Query: ").append(example.getQuery().toPrettyString()).append("\n\n");
        }
        
        String result = examples.toString();
        System.out.println("✅ Dynamic examples built successfully");
        System.out.println("📏 Total length: " + result.length() + " characters");
        System.out.println("📋 Preview (first 300 chars):");
        System.out.println("   " + result.substring(0, Math.min(300, result.length())) + "...");
        System.out.println("🎯 ===== DYNAMIC EXAMPLES COMPLETED =====\n");
        
        return result;
    }
}
