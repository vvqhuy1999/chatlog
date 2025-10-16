package com.example.chatlog.service.impl;

import com.example.chatlog.dto.DataExample;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseIndexingService {

    @Autowired
    private VectorStore vectorStore;

    private final File vectorStoreFile = new File("vector_store.json");

    @PostConstruct
    public void indexKnowledgeBase() {
        // CHỈ CHẠY NẾU FILE VECTOR CHƯA TỒN TẠI
        if (vectorStoreFile.exists()) {
            System.out.println("✅ Kho tri thức vector đã tồn tại. Bỏ qua bước indexing.");
            return;
        }

        System.out.println("🚀 Bắt đầu quá trình vector hóa kho tri thức...");
        
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
        ObjectMapper objectMapper = new ObjectMapper();
        List<Document> documents = new ArrayList<>();

        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});

                for (DataExample example : examples) {
                    if (example.getQuestion() != null && example.getQuery() != null) {
                        Document doc = new Document(
                            example.getQuestion(),
                            Map.of(
                                "question", example.getQuestion(),
                                "query_dsl", example.getQuery().toString(),
                                "source_file", fileName
                            )
                        );
                        documents.add(doc);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi đọc file " + fileName + ": " + e.getMessage());
            }
        }

        // Đưa documents vào Vector Store (trong bộ nhớ)
        vectorStore.add(documents);
        
        // Lưu toàn bộ Vector Store xuống file
        if (vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore) {
            ((org.springframework.ai.vectorstore.SimpleVectorStore) vectorStore).save(vectorStoreFile);
        }
        
        System.out.println("✅ Đã vector hóa và lưu trữ " + documents.size() + " ví dụ vào file " + vectorStoreFile.getAbsolutePath());
    }
}
