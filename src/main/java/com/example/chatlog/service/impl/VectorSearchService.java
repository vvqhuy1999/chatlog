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

        if (similarDocuments.isEmpty()) {
            return "No relevant examples found in the vector store.";
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
