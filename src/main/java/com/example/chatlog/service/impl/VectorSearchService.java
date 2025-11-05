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
            // Theo yêu cầu: 8 kết quả từ vector + 2 kết quả từ keyword
            System.out.println("   ✅ Strategy: 8 vector + 2 keyword (total 10)");
            resultMode = "VECTOR+KEYWORD";

            // 8 từ vector similarity
            List<AiEmbedding> vectorTop = aiEmbeddingService.findSimilarEmbeddings(
                queryEmbeddingString, 8
            );
            // 2 từ keyword full-text
            List<AiEmbedding> keywordTop = aiEmbeddingService.fullTextSearch(
                keywords, 10
            );

            // Hợp nhất: ưu tiên vector, thêm 2 từ keyword không trùng id
            java.util.LinkedHashMap<String, AiEmbedding> merged = new java.util.LinkedHashMap<>();
            for (AiEmbedding e : vectorTop) merged.put(e.getId().toString(), e);
            int added = 0;
            for (AiEmbedding e : keywordTop) {
                if (added >= 2) break;
                String key = e.getId().toString();
                if (!merged.containsKey(key)) {
                    merged.put(key, e);
                    added++;
                }
            }

            similarEmbeddings = new java.util.ArrayList<>(merged.values());
            // Nếu < 10, bổ sung thêm từ keywordTop cho đủ (không vượt 10)
            for (AiEmbedding e : keywordTop) {
                if (similarEmbeddings.size() >= 10) break;
                String key = e.getId().toString();
                if (!merged.containsKey(key)) {
                    similarEmbeddings.add(e);
                }
            }
            System.out.println("   🧪 Vector selected: " + vectorTop.size() + ", Keyword added: " + added + ", Total: " + similarEmbeddings.size());
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
            Object qMeta = embedding.getMetadata() != null ? embedding.getMetadata().get("question") : null;
            if (qMeta != null) {
                examples.append("Question: ").append(qMeta).append("\n");
            }
            // Content preview (để luôn thấy tiêu chí tìm kiếm từ kho tri thức)
            String content = embedding.getContent();
            if (content != null && !content.isEmpty()) {
                String preview = content.length() > 180 ? content.substring(0, 180) + "..." : content;
                examples.append("Content: ").append(preview).append("\n");
            }
            
            // Include scenario
            Object scenario = embedding.getMetadata().get("scenario");
            if (scenario != null) {
                examples.append("Scenario: ").append(scenario).append("\n");
            }
            // Include phase
            Object phase = embedding.getMetadata().get("phase");
            if (phase != null) {
                examples.append("Phase: ").append(phase).append("\n");
            }
            Object qdsl = embedding.getMetadata() != null ? embedding.getMetadata().get("query_dsl") : null;
            if (qdsl != null) {
                examples.append("Query: ").append(qdsl).append("\n\n");
            } else {
                examples.append("\n");
            }
        }

        // ===== HYBRID SCORE DEBUG (optional) =====
        if (queryEmbeddingString != null && keywords != null && !keywords.isEmpty()) {
            try {
                java.util.List<java.util.Map<String, Object>> dbg =
                        aiEmbeddingService.hybridSearchDebug(queryEmbeddingString, keywords, 10);
                if (!dbg.isEmpty()) {
                    examples.append("===== HYBRID SCORE DEBUG =====\n\n");
                    for (int i = 0; i < Math.min(10, dbg.size()); i++) {
                        java.util.Map<String, Object> row = dbg.get(i);
                        double fs = toDouble(row.get("final_score"));
                        double ss = toDouble(row.get("similarity_score"));
                        double ks = toDouble(row.get("keyword_score"));
                        String title = null;
                        Object metaObj = row.get("metadata");
                        if (metaObj instanceof java.util.Map) {
                            Object qq = ((java.util.Map<?, ?>) metaObj).get("question");
                            if (qq != null) title = qq.toString();
                        } else if (metaObj != null) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                java.util.Map<String, Object> pm =
                                        om.readValue(metaObj.toString(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){});
                                Object qq = pm.get("question");
                                if (qq != null) title = qq.toString();
                            } catch (Exception ignore) {
                                // fallback to content snippet
                            }
                        }
                        if (title == null) {
                            Object raw = row.get("content");
                            if (raw != null) {
                                String s = raw.toString();
                                title = s.length() > 120 ? s.substring(0, 120) + "..." : s;
                            }
                        }
                        examples.append(String.format(
                                "#%d final=%.4f (semantic=%.4f, keyword=%.4f) | %s%n",
                                i + 1, fs, ss, ks, title != null ? title : ""));
                    }
                    examples.append("\n");
                }
            } catch (Exception ex) {
                System.out.println("   ⚠️ Debug summary build failed: " + ex.getMessage());
            }
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

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }
}
