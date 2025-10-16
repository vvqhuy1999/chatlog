package com.example.chatlog.service.impl;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {

    @Autowired
    private VectorStore vectorStore;

    public String findRelevantExamples(String userQuery) {
        System.out.println("🧠 Thực hiện tìm kiếm ngữ nghĩa cho: \"" + userQuery + "\"");

        // Tìm ví dụ có ý nghĩa gần nhất
        List<Document> similarDocuments = vectorStore.similaritySearch(userQuery);
        
        // 🔍 DEBUG: Kiểm tra kết quả tìm kiếm
        System.out.println("[VectorSearchService] 🔍 DEBUG: Số lượng kết quả tìm được: " + similarDocuments.size());
        
        if (similarDocuments.isEmpty()) {
            System.out.println("[VectorSearchService] ⚠️ WARNING: Vector store không tìm thấy ví dụ tương đồng!");
            System.out.println("[VectorSearchService] 💡 Có thể nguyên nhân:");
            System.out.println("   1. vector_store.json chưa được tạo hoặc chưa được load");
            System.out.println("   2. Embedding model chưa khởi tạo");
            System.out.println("   3. Query quá khác biệt với các examples");
            return "⚠️ Không tìm thấy ví dụ tương đồng trong vector store.\n\nGợi ý: Kiểm tra xem vector_store.json đã được tạo và ứng dụng đã khởi tạo EmbeddingClient chưa.";
        }
        
        // Log chi tiết mỗi kết quả
        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            System.out.println("[VectorSearchService] Result " + (i+1) + ": " + doc.getMetadata().get("question"));
        }
        
        // Chuyển đổi kết quả tìm được thành chuỗi để đưa vào prompt
        StringBuilder examples = new StringBuilder();
        examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):\n\n");

        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            examples.append("Example ").append(i + 1).append(":\n");
            examples.append("Question: ").append(doc.getMetadata().get("question")).append("\n");
            examples.append("Query: ").append(doc.getMetadata().get("query_dsl")).append("\n\n");
        }
        
        System.out.println("✅ Tìm thấy " + similarDocuments.size() + " ví dụ tương đồng.");
        return examples.toString();
    }
}
