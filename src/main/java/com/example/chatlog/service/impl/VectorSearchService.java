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
// @Transactional("secondaryTransactionManager")
public class VectorSearchService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private AiEmbeddingService aiEmbeddingService;

    /**
     * VECTOR SEARCH: Tìm kiếm semantic similarity thuần túy
     */
    public String findRelevantExamples(String userQuery) {
        long startTime = System.currentTimeMillis();
        
        System.out.println("\n" + "═".repeat(80));
        System.out.println("🔍 [VectorSearch] START");
        System.out.println("   📝 Query: \"" + (userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery) + "\"");
        
        // BƯỚC 1: Tạo Query Embedding cho semantic search
        String queryEmbeddingString = null;
        int embeddingDimensions = 0;
        
        if (embeddingModel != null) {
            try {
                long embeddingStart = System.currentTimeMillis();
                float[] queryEmbedding = embeddingModel.embed(userQuery);
                embeddingDimensions = queryEmbedding.length;
                
                // Convert to PostgreSQL vector format - optimized với StringBuilder capacity
                StringBuilder sb = new StringBuilder(queryEmbedding.length * 12);
                sb.append("[");
                for (int i = 0; i < queryEmbedding.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(queryEmbedding[i]);
                }
                sb.append("]");
                queryEmbeddingString = sb.toString();
                
                long embeddingTime = System.currentTimeMillis() - embeddingStart;
                System.out.println("   ✅ [Step 1] Embedding created: " + embeddingDimensions + " dims in " + embeddingTime + "ms");
            } catch (Exception e) {
                System.out.println("   ❌ [Step 1] ERROR creating embedding: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("   ❌ [Step 1] ERROR: EmbeddingModel is NULL!");
        }
        
        // BƯỚC 2: Vector Search
        List<AiEmbedding> similarEmbeddings;
        String resultMode = "VECTOR";
        int topK = 8; // Mặc định 8 kết quả
        
        if (queryEmbeddingString != null) {
            long dbStart = System.currentTimeMillis();
            similarEmbeddings = aiEmbeddingService.findSimilarEmbeddings(queryEmbeddingString, topK);
            long dbTime = System.currentTimeMillis() - dbStart;
            System.out.println("   ✅ [Step 2] DB search: " + similarEmbeddings.size() + " results in " + dbTime + "ms");
        } else {
            similarEmbeddings = List.of();
            resultMode = "NONE";
            System.out.println("   ⚠️ [Step 2] SKIPPED - No embedding available");
        }
        
        if (similarEmbeddings.isEmpty()) {
            System.out.println("   ⚠️ [VectorSearch] No results found!");
            System.out.println("═".repeat(80) + "\n");
            return "⚠️ Không tìm thấy ví dụ tương đồng.";
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("   ✅ [VectorSearch] DONE - " + similarEmbeddings.size() + " examples in " + totalTime + "ms");
        System.out.println("═".repeat(80) + "\n");
        
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

        return examples.toString();
    }

}
