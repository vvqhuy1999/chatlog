package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ai.AiEmbedding;
import com.example.chatlog.service.AiEmbeddingService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional("secondaryTransactionManager")
public class VectorSearchService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private AiEmbeddingService aiEmbeddingService;

    /**
     * HYBRID SEARCH: Kết hợp Semantic + Keyword matching
     * Formula: Final Score = (Semantic Score × 0.7) + (Keyword Score × 0.3)
     */
    public String findRelevantExamples(String userQuery) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 HYBRID SEARCH - SEMANTIC + KEYWORD MATCHING");
        System.out.println("=".repeat(100));
        
        System.out.println("\n📝 QUERY: \"" + userQuery + "\"");
        
        // BƯỚC 1: Tạo Query Embedding cho semantic search
        float[] queryEmbedding = null;
        String queryEmbeddingString = null;
        
        if (embeddingModel != null) {
            try {
                System.out.println("\n🔄 STEP 1: Creating Query Embedding for Semantic Search");
                queryEmbedding = embeddingModel.embed(userQuery);
                
                // Convert to PostgreSQL vector format
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < queryEmbedding.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(queryEmbedding[i]);
                }
                sb.append("]");
                queryEmbeddingString = sb.toString();
                
                System.out.println("   ✅ Query Embedding Created: " + queryEmbedding.length + " dimensions");
            } catch (Exception e) {
                System.out.println("   ❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // BƯỚC 2: Extract keywords từ user query
        System.out.println("\n🔍 STEP 2: Extracting Keywords from Query");
        String keywords = extractKeywords(userQuery);
        System.out.println("   ✅ Extracted keywords: \"" + keywords + "\"");
        System.out.println("   📝 Keywords will be searched in: metadata->keywords array, question, and content");
        
        // BƯỚC 3: Hybrid Search
        System.out.println("\n🎯 STEP 3: Hybrid Search (70% Semantic + 30% Keyword)");
        
        List<AiEmbedding> similarEmbeddings;
        String resultMode = "";
        int topK = 8; // Số lượng kết quả mong muốn
        
        if (queryEmbeddingString != null && !keywords.isEmpty()) {
            // Hybrid search: Kết hợp vector similarity + keyword matching
            similarEmbeddings = aiEmbeddingService.hybridSearch(
                queryEmbeddingString, 
                keywords, 
                topK
            );
            System.out.println("   ✅ Used: HYBRID SEARCH (Semantic + Keyword)");
            resultMode = "HYBRID";

            // Debug: in ra điểm số tính toán
            try {
                List<java.util.Map<String, Object>> debugRows = aiEmbeddingService.hybridSearchDebug(
                    queryEmbeddingString, keywords, topK
                );
                // đã in trong service; nếu cần thêm, có thể in ở đây
            } catch (Exception e) {
                System.out.println("   ⚠️ Debug print failed: " + e.getMessage());
            }
        } else if (queryEmbeddingString != null) {
            // Fallback: Chỉ dùng semantic search
            similarEmbeddings = aiEmbeddingService.findSimilarEmbeddings(
                queryEmbeddingString, 
                topK
            );
            System.out.println("   ⚠️ Fallback: SEMANTIC SEARCH only");
            resultMode = "SEMANTIC";
        } else if (!keywords.isEmpty()) {
            // Fallback: Chỉ dùng keyword search
            similarEmbeddings = aiEmbeddingService.fullTextSearch(keywords, topK);
            System.out.println("   ⚠️ Fallback: KEYWORD SEARCH only");
            resultMode = "KEYWORD";
        } else {
            similarEmbeddings = List.of();
            System.out.println("   ❌ No search method available");
            resultMode = "NONE";
        }
        
        System.out.println("   ✅ Found: " + similarEmbeddings.size() + " similar embeddings");
        
        if (similarEmbeddings.isEmpty()) {
            System.out.println("   ⚠️ No similar documents found!");
            return "⚠️ Không tìm thấy ví dụ tương đồng.";
        }
        
        // BƯỚC 4: Convert và hiển thị kết quả
        System.out.println("\n📊 STEP 4: Results Analysis");
        System.out.println("-".repeat(100));
        
        for (int i = 0; i < similarEmbeddings.size(); i++) {
            AiEmbedding embedding = similarEmbeddings.get(i);
            String question = (String) embedding.getMetadata().get("question");
            String scenario = (String) embedding.getMetadata().get("scenario");
            
            System.out.println("\n[RANK #" + (i+1) + "] " + question);
            if (scenario != null) {
                System.out.println("   📁 Scenario: " + scenario);
            }
            System.out.println("   🎯 Matched by: Hybrid Score (Semantic + Keyword)");
        }
        
        System.out.println("\n" + "-".repeat(100));
        
        // Format kết quả cho LLM
        StringBuilder examples = new StringBuilder();
        examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE\n");
        examples.append("Mode: ").append(resultMode).append("\n\n");

        for (int i = 0; i < similarEmbeddings.size(); i++) {
            AiEmbedding embedding = similarEmbeddings.get(i);
            examples.append("Example ").append(i + 1).append(":\n");
            examples.append("Question: ").append(embedding.getMetadata().get("question")).append("\n");
            
            // Include scenario if available
            Object scenario = embedding.getMetadata().get("scenario");
            if (scenario != null) {
                examples.append("Scenario: ").append(scenario).append("\n");
            }
            
            // Include phase if available
            Object phase = embedding.getMetadata().get("phase");
            if (phase != null) {
                examples.append("Phase: ").append(phase).append("\n");
            }
            
            examples.append("Query: ").append(embedding.getMetadata().get("query_dsl")).append("\n\n");
        }
        
        System.out.println("\n✅ Total: " + similarEmbeddings.size() + " examples found using HYBRID SEARCH");
        System.out.println("=".repeat(100) + "\n");
        
        return examples.toString();
    }

    /**
     * Extract keywords từ user query
     * Loại bỏ stop words và giữ lại các từ khóa quan trọng
     * Trả về chuỗi các từ khóa để tìm kiếm trong keywords array
     */
    private String extractKeywords(String query) {
        // Stop words tiếng Việt và tiếng Anh
        List<String> stopWords = Arrays.asList(
            "là", "của", "và", "có", "trong", "từ", "được", "cho", "để", "này", "đó",
            "the", "is", "are", "in", "on", "at", "to", "for", "of", "a", "an",
            "what", "which", "who", "when", "where", "why", "how",
            "gì", "nào", "ai", "khi", "ở", "đâu", "tại", "sao", "như", "thế", "nào",
            "bao", "nhiêu", "của", "với", "về"
        );
        
        // Lowercase và tách từ
        String[] words = query.toLowerCase()
            .replaceAll("[^a-z0-9\\sáàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ]", " ")
            .split("\\s+");
        
        // Filter stop words và từ ngắn, giữ lại các từ quan trọng
        List<String> keywords = Arrays.stream(words)
            .filter(word -> word.length() > 2)
            .filter(word -> !stopWords.contains(word))
            .distinct()
            .collect(Collectors.toList());
        
        // Nếu có ít từ, thêm các từ ghép phổ biến
        if (keywords.size() <= 2 && query.length() > 10) {
            // Thêm các cụm từ phổ biến từ query gốc
            String lower = query.toLowerCase();
            if (lower.contains("truy cập") || lower.contains("truy cập")) {
                keywords.add("truy cập");
            }
            if (lower.contains("website") || lower.contains("trang web")) {
                keywords.add("website");
            }
            if (lower.contains("ip") || lower.contains("địa chỉ")) {
                keywords.add("ip");
            }
            if (lower.contains("user") || lower.contains("người dùng")) {
                keywords.add("user");
            }
        }
        
        return String.join(" ", keywords);
    }
}
