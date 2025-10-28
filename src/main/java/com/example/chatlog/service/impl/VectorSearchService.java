package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ai.AiEmbedding;
import com.example.chatlog.service.AiEmbeddingService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional("secondaryTransactionManager")  // SỬ DỤNG TRANSACTION MANAGER PHỤ
public class VectorSearchService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private AiEmbeddingService aiEmbeddingService;

    public String findRelevantExamples(String userQuery) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 VECTOR SEARCH - EMBEDDING & COMPARISON (Database)");
        System.out.println("=".repeat(100));
        
        // BƯỚC 1: Tạo Query Embedding
        System.out.println("\n📝 QUERY: \"" + userQuery + "\"");
        
        float[] queryEmbedding = null;
        if (embeddingModel != null) {
            try {
                System.out.println("\n🔄 STEP 1: Creating Query Embedding");
                System.out.println("   Calling: embeddingModel.embed(userQuery)");
                
                queryEmbedding = embeddingModel.embed(userQuery);
                
                System.out.println("   ✅ Query Embedding Created:");
                System.out.println("      Dimensions: " + queryEmbedding.length);
                
                // Hiển thị first 10 values
                System.out.print("      First 10 values: [");
                for (int i = 0; i < Math.min(10, queryEmbedding.length); i++) {
                    System.out.print(String.format("%.4f", queryEmbedding[i]));
                    if (i < Math.min(10, queryEmbedding.length) - 1) System.out.print(", ");
                }
                System.out.println("]");
                
                // Hiển thị last 10 values
                System.out.print("      Last 10 values: [");
                for (int i = Math.max(0, queryEmbedding.length - 10); i < queryEmbedding.length; i++) {
                    System.out.print(String.format("%.4f", queryEmbedding[i]));
                    if (i < queryEmbedding.length - 1) System.out.print(", ");
                }
                System.out.println("]");
                
                // Tính magnitude
                double magnitude = 0;
                for (float val : queryEmbedding) {
                    magnitude += val * val;
                }
                magnitude = Math.sqrt(magnitude);
                System.out.println("      Magnitude: " + String.format("%.6f", magnitude));
                
            } catch (Exception e) {
                System.out.println("   ❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // BƯỚC 2: Similarity Search từ Database với SearchRequest
        System.out.println("\n🔍 STEP 2: Similarity Search from Database");
        
        // Tạo SearchRequest theo Spring AI API pattern
        SearchRequest searchRequest = SearchRequest.builder()
            .query(userQuery)
            .topK(8)  // Lấy 8 ví dụ tương tự nhất
            .build();
        
        System.out.println("   Using SearchRequest with topK=" + searchRequest.getTopK());
        System.out.println("   → Searching for " + searchRequest.getTopK() + " most similar embeddings using vector similarity");
        
        List<AiEmbedding> similarEmbeddings = null;
        if (queryEmbedding != null) {
            // Convert float[] to PostgreSQL vector format: "[0.1,0.2,0.3,...]"
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < queryEmbedding.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(queryEmbedding[i]);
            }
            sb.append("]");
            String queryEmbeddingString = sb.toString();
            
            // Query với topK từ SearchRequest
            similarEmbeddings = aiEmbeddingService.findSimilarEmbeddings(queryEmbeddingString, searchRequest.getTopK());
        } else {
            similarEmbeddings = List.of();
        }
        
        System.out.println("   ✅ Found: " + similarEmbeddings.size() + " similar embeddings (topK=" + searchRequest.getTopK() + ")");
        
        if (similarEmbeddings.isEmpty()) {
            System.out.println("   ⚠️ No similar documents found in database!");
            return "⚠️ Không tìm thấy ví dụ tương đồng trong database.";
        }
        
        // Convert AiEmbedding to Document format for compatibility
        List<Document> similarDocuments = new java.util.ArrayList<>();
        for (AiEmbedding embedding : similarEmbeddings) {
            Document doc = new Document(
                embedding.getContent(),
                embedding.getMetadata()
            );
            similarDocuments.add(doc);
        }
        
        // BƯỚC 3: Hiển thị chi tiết so sánh
        System.out.println("\n📊 STEP 3: Similarity Comparison Details");
        System.out.println("-".repeat(100));
        
        for (int i = 0; i < Math.min(8, similarDocuments.size()); i++) {
            Document doc = similarDocuments.get(i);
            String question = (String) doc.getMetadata().get("question");
            
            System.out.println("\n[RANK #" + (i+1) + "] " + question);
            System.out.println("   Document Object: " + doc.toString().substring(0, Math.min(150, doc.toString().length())) + "...");
            
            // Nếu có query embedding, tính similarity
            if (queryEmbedding != null) {
                System.out.println("   ");
                System.out.println("   🧮 Cosine Similarity Calculation:");
                
                // Hiển thị first 5 values của query embedding
                System.out.print("      Query Embedding: [");
                for (int j = 0; j < Math.min(5, queryEmbedding.length); j++) {
                    System.out.print(String.format("%.4f", queryEmbedding[j]));
                    if (j < Math.min(5, queryEmbedding.length) - 1) System.out.print(", ");
                }
            }
        }
        
        System.out.println("\n" + "-".repeat(100));
        
        // Format kết quả cho LLM
        StringBuilder examples = new StringBuilder();
        examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search from Database):\n\n");

        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            examples.append("Example ").append(i + 1).append(":\n");
            examples.append("Question: ").append(doc.getMetadata().get("question")).append("\n");
            examples.append("Query: ").append(doc.getMetadata().get("query_dsl")).append("\n\n");
        }
        
        System.out.println("\n✅ Total: " + similarDocuments.size() + " examples found");
        System.out.println("=".repeat(100) + "\n");
        
        return examples.toString();
    }
}
