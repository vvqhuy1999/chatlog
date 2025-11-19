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
     * VECTOR SEARCH: Tìm kiếm semantic similarity thuần túy
     */
    public String findRelevantExamples(String userQuery) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 VECTOR SEMANTIC SEARCH");
        System.out.println("=".repeat(100));
        
        // Check database stats first
        long totalEmbeddings = aiEmbeddingService.countAllNotDeleted();
        System.out.println("\n📊 DATABASE STATS:");
        System.out.println("   Total embeddings in database: " + totalEmbeddings);
        
        if (totalEmbeddings == 0) {
            System.out.println("   ⚠️ WARNING: No embeddings found in database!");
            System.out.println("   Please run the embedding import process first.");
        }
        
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
        
        // BƯỚC 2: Vector Search
        System.out.println("\n🎯 STEP 2: Vector Semantic Search");
        
        List<AiEmbedding> similarEmbeddings;
        String resultMode = "VECTOR";
        int topK = 8; // Lấy 10 kết quả tốt nhất
        
        if (queryEmbeddingString != null) {
            // Lấy 10 kết quả tốt nhất từ vector similarity search
            System.out.println("   ✅ Strategy: Pure vector search for top 10 most relevant examples");

            similarEmbeddings = aiEmbeddingService.findSimilarEmbeddings(
                queryEmbeddingString, topK
            );
            
            System.out.println("   📊 Vector results: " + similarEmbeddings.size());
            System.out.println("   🧪 Final result: " + similarEmbeddings.size() + " examples");
        } else {
            similarEmbeddings = List.of();
            System.out.println("   ❌ No embedding model available");
            resultMode = "NONE";
        }
        
        System.out.println("   ✅ Found: " + similarEmbeddings.size() + " similar embeddings");
        
        if (similarEmbeddings.isEmpty()) {
            System.out.println("   ⚠️ No similar documents found!");
            return "⚠️ Không tìm thấy ví dụ tương đồng.";
        }
        
        // BƯỚC 3: Convert và hiển thị kết quả
        System.out.println("\n📊 STEP 3: Results Analysis");
        System.out.println("-".repeat(100));
        
        for (int i = 0; i < similarEmbeddings.size(); i++) {
            AiEmbedding embedding = similarEmbeddings.get(i);
            String question = (String) embedding.getMetadata().get("question");
            String scenario = (String) embedding.getMetadata().get("scenario");
            
            System.out.println("\n[RANK #" + (i+1) + "] " + question);
            if (scenario != null) {
                System.out.println("   📁 Scenario: " + scenario);
            }
            System.out.println("   🎯 Matched by: Vector Similarity Score");
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

        System.out.println("\n✅ Total: " + similarEmbeddings.size() + " examples found using VECTOR SEARCH");
        System.out.println("=".repeat(100) + "\n");
        
        return examples.toString();
    }

}
