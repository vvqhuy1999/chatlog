package com.example.chatlog.service.impl;

import com.example.chatlog.dto.DataExample;
import com.example.chatlog.service.AiEmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseIndexingService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private AiEmbeddingService aiEmbeddingService;

    @PostConstruct
    public void indexKnowledgeBase() {
        System.out.println("🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...");
        
        // Đếm số embeddings hiện có
        long existingCount = aiEmbeddingService.countAllNotDeleted();
        System.out.println("📊 Hiện có " + existingCount + " embeddings trong database");

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
        int totalSaved = 0;

        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});

                for (DataExample example : examples) {
                    if (example.getQuestion() != null && example.getQuery() != null) {
                        // Kiểm tra xem embedding đã tồn tại chưa
                        if (aiEmbeddingService.existsByContent(example.getQuestion())) {
                            continue; // Bỏ qua nếu đã tồn tại
                        }
                        
                        // 🔧 Chuyển JsonNode thành Object rồi serialize thành JSON string
                        Object queryDslObj = objectMapper.treeToValue(example.getQuery(), Object.class);
                        String queryDslJson = objectMapper.writeValueAsString(queryDslObj);
                        
                        // Tạo embedding cho câu hỏi
                        String embeddingString = null;
                        if (embeddingModel != null) {
                            try {
                                float[] embedding = embeddingModel.embed(example.getQuestion());
                                // Convert float[] to PostgreSQL vector format: "[0.1, 0.2, 0.3, ...]"
                                StringBuilder sb = new StringBuilder("[");
                                for (int i = 0; i < embedding.length; i++) {
                                    if (i > 0) sb.append(",");
                                    sb.append(embedding[i]);
                                }
                                sb.append("]");
                                embeddingString = sb.toString();
                            } catch (Exception e) {
                                System.err.println("❌ Lỗi tạo embedding cho: " + example.getQuestion());
                                e.printStackTrace();
                            }
                        }

                        // Chuẩn bị metadata
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("question", example.getQuestion());
                        metadata.put("query_dsl", queryDslJson);
                        metadata.put("source_file", fileName);

                        // Lưu embedding vào database
                        if (embeddingString != null) {
                            aiEmbeddingService.saveEmbedding(
                                example.getQuestion(),
                                embeddingString,
                                metadata
                            );
                            totalSaved++;
                        }

                        // Chuẩn bị document cho Vector Store (trong bộ nhớ)
                        Document doc = new Document(
                            example.getQuestion(),
                            metadata
                        );
                        documents.add(doc);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi đọc file " + fileName + ": " + e.getMessage());
            }
        }

        // Đưa documents vào Vector Store (trong bộ nhớ)
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        
        long finalCount = aiEmbeddingService.countAllNotDeleted();
        System.out.println("✅ Đã thêm " + totalSaved + " embeddings mới vào Database");
        System.out.println("📊 Tổng số embeddings hiện tại: " + finalCount);
    }

    public List<DataExample> getExampleLibrary() {
        List<DataExample> exampleLibrary = new ArrayList<>();
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
        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});
                exampleLibrary.addAll(examples);
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi đọc file " + fileName + ": " + e.getMessage());
            }
        }
        return exampleLibrary;
    }
}
