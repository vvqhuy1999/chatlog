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
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional("secondaryTransactionManager")  // BỌC TOÀN BỘ PHƯƠNG THỨC TRONG TRANSACTION PHỤ
    public void indexKnowledgeBase() {
        System.out.println("🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...");

        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json"
        };
        ObjectMapper objectMapper = new ObjectMapper();
        List<Document> documents = new ArrayList<>();
        int totalSaved = 0;

        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(inputStream, new TypeReference<List<DataExample>>() {});

                // Đếm số entries trong file JSON
                int fileCount = examples.size();
                
                // Đếm số embeddings hiện có trong database cho file này
                long dbCount = aiEmbeddingService.countBySourceFile(fileName);
                
                System.out.println("📁 File: " + fileName);
                System.out.println("   📊 Số entries trong file: " + fileCount);
                System.out.println("   💾 Số embeddings trong DB: " + dbCount);
                
                // So sánh và quyết định có cần xử lý không
                if (fileCount == dbCount) {
                    System.out.println("   ✅ Dữ liệu đã đồng bộ, bỏ qua file này");
                    continue;
                } else if (fileCount < dbCount) {
                    System.out.println("   ⚠️ Cảnh báo: DB có nhiều records hơn file (" + dbCount + " > " + fileCount + ")");
                    System.out.println("   💡 Có thể file đã bị xóa bớt entries. Tiếp tục xử lý...");
                } else {
                    int newEntriesCount = fileCount - (int)dbCount;
                    System.out.println("   🆕 Phát hiện " + newEntriesCount + " entries mới cần thêm vào DB");
                }

                // Tính số records cần thêm vào DB
                int newEntriesCount = fileCount - (int)dbCount;
                
                // Chỉ lấy các records CUỐI CÙNG của file JSON (số lượng = newEntriesCount)
                // Ví dụ: file có 231 records, DB có 227 records → chỉ lấy 4 records cuối cùng (index 227-230)
                List<DataExample> examplesToProcess = new ArrayList<>();
                if (newEntriesCount > 0 && newEntriesCount <= examples.size()) {
                    // Lấy newEntriesCount records cuối cùng
                    int startIndex = examples.size() - newEntriesCount;
                    examplesToProcess = examples.subList(startIndex, examples.size());
                    System.out.println("   📋 Chỉ xử lý " + newEntriesCount + " records cuối cùng (từ index " + startIndex + " đến " + (examples.size() - 1) + ")");
                } else if (newEntriesCount > examples.size()) {
                    // Trường hợp đặc biệt: newEntriesCount > examples.size() (không nên xảy ra)
                    System.out.println("   ⚠️ Cảnh báo: newEntriesCount (" + newEntriesCount + ") > examples.size() (" + examples.size() + "), xử lý tất cả");
                    examplesToProcess = examples;
                } else {
                    // newEntriesCount <= 0, không cần xử lý
                    System.out.println("   ℹ️ Không có records mới cần xử lý");
                    examplesToProcess = new ArrayList<>();
                }

                // Chỉ xử lý các entries cuối cùng chưa có trong database
                int processedCount = 0;
                for (DataExample example : examplesToProcess) {
                    if (example.getQuestion() != null && example.getQuery() != null) {
                        // Kiểm tra xem embedding đã tồn tại chưa - bỏ qua nếu đã tồn tại
                        // Check này để tránh tạo embedding không cần thiết
                        if (aiEmbeddingService.existsByContent(example.getQuestion())) {
                            continue; // Bỏ qua nếu đã tồn tại
                        }
                        
                        processedCount++;
                        
                        // 🔧 Chuyển JsonNode thành Object rồi serialize thành JSON string
                        Object queryDslObj = objectMapper.treeToValue(example.getQuery(), Object.class);
                        String queryDslJson = objectMapper.writeValueAsString(queryDslObj);
                        
                        // Tạo embedding cho câu hỏi
                        float[] embedding = null;
                        if (embeddingModel != null) {
                            try {
                                embedding = embeddingModel.embed(example.getQuestion());
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
                        metadata.put("keywords", example.getKeywords());

                        // Lưu embedding vào database - saveEmbedding() sẽ tự check duplicate
                        // saveEmbedding() sử dụng WHERE NOT EXISTS trong SQL nên an toàn với race condition
                        if (embedding != null) {
                            // Convert float[] to PostgreSQL vector format: "[0.1,0.2,0.3,...]"
                            StringBuilder sb = new StringBuilder("[");
                            for (int i = 0; i < embedding.length; i++) {
                                if (i > 0) sb.append(",");
                                sb.append(embedding[i]);
                            }
                            sb.append("]");
                            String embeddingString = sb.toString();
                            
                            // Kiểm tra xem record có tồn tại trước khi save không
                            boolean existedBefore = aiEmbeddingService.existsByContent(example.getQuestion());
                            
                            // Lưu thời gian trước khi save để kiểm tra xem record có phải mới không
                            java.time.OffsetDateTime beforeSave = java.time.OffsetDateTime.now().minusSeconds(1);
                            
                            // Gọi saveEmbedding() - method này sẽ tự check duplicate bằng WHERE NOT EXISTS
                            // Nếu record đã tồn tại, method sẽ return existing record (với createdAt cũ)
                            // Nếu record mới, method sẽ insert và return new record (với createdAt mới)
                            com.example.chatlog.entity.ai.AiEmbedding savedEmbedding = aiEmbeddingService.saveEmbedding(
                                example.getQuestion(),
                                embeddingString,
                                metadata
                            );
                            
                            // Kiểm tra xem record có phải là record mới không
                            // Cách 1: Nếu trước đó không tồn tại và sau đó tồn tại, thì là record mới
                            // Cách 2: Kiểm tra createdAt - record mới sẽ có createdAt gần với thời gian hiện tại
                            java.time.OffsetDateTime afterSave = java.time.OffsetDateTime.now().plusSeconds(1);
                            boolean isNewRecord = false;
                            
                            if (!existedBefore) {
                                // Nếu trước đó không tồn tại, kiểm tra createdAt để đảm bảo là record mới
                                if (savedEmbedding.getCreatedAt() != null && 
                                    savedEmbedding.getCreatedAt().isAfter(beforeSave) && 
                                    savedEmbedding.getCreatedAt().isBefore(afterSave)) {
                                    isNewRecord = true;
                                }
                            }
                            // Nếu existedBefore = true, thì chắc chắn không phải record mới
                            
                            if (isNewRecord) {
                                // Chỉ tăng totalSaved và add document nếu thực sự insert mới
                                totalSaved++;
                                
                                // Chỉ add document vào vectorStore nếu thực sự insert mới vào DB
                                // SimpleVectorStore chỉ là in-memory cache, không lưu vào DB
                                Document doc = new Document(
                                    example.getQuestion(),
                                    metadata
                                );
                                documents.add(doc);
                            }
                        }
                    }
                }
                
                System.out.println("   ✅ Đã xử lý " + processedCount + " entries mới từ file " + fileName);
                
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi đọc file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Đưa documents vào Vector Store (trong bộ nhớ)
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        
        long finalCount = aiEmbeddingService.countAllNotDeleted();
        System.out.println("\n📊 === KẾT QUẢ TỔNG HỢP ===");
        System.out.println("✅ Đã thêm " + totalSaved + " embeddings mới vào Database");
        System.out.println("📊 Tổng số embeddings hiện tại trong DB: " + finalCount);
        System.out.println("🎉 Hoàn thành quá trình đồng bộ!");
    }

    public List<DataExample> getExampleLibrary() {
        List<DataExample> exampleLibrary = new ArrayList<>();
        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json"
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
