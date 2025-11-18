package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ai.AiEmbedding;
import com.example.chatlog.repository.AiEmbeddingRepository;
import com.example.chatlog.service.AiEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("secondaryTransactionManager")  // CHỈ ĐỊNH RÕ TRANSACTION MANAGER PHỤ
public class AiEmbeddingServiceImpl implements AiEmbeddingService {

    private static final Logger VECTOR_SEARCH_LOGGER = LoggerFactory.getLogger("VECTOR_SEARCH_DEBUG");
    private final AiEmbeddingRepository aiEmbeddingRepository;

    @Override
    public AiEmbedding saveEmbedding(String content, String embedding, Map<String, Object> metadata) {
        // Check duplicate trước để tránh duplicate insertion (double-check pattern)
        Optional<AiEmbedding> existing = aiEmbeddingRepository.findByContentAndNotDeleted(content);
        if (existing.isPresent()) {
            // Nếu đã tồn tại, return existing record thay vì tạo mới
            return existing.get();
        }
        
        // Generate ID and timestamps manually (since @PrePersist won't be triggered)
        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        
        // Convert metadata Map to JSON String
        String metadataJson = convertMapToJson(metadata);
        
        // Use custom native query with explicit vector cast
        // Query sẽ tự check duplicate bằng WHERE NOT EXISTS để đảm bảo an toàn
        int rowsAffected = aiEmbeddingRepository.saveWithVectorCast(
            id,
            content,
            embedding,
            metadataJson,
            now,  // createdAt
            now,  // updatedAt
            0     // isDeleted
        );
        
        // Nếu insert thành công (rowsAffected > 0), return new entity
        if (rowsAffected > 0) {
            return AiEmbedding.builder()
                    .id(id)
                    .content(content)
                    .embedding(embedding)
                    .metadata(metadata)
                    .createdAt(now)
                    .updatedAt(now)
                    .isDeleted(0)
                    .build();
        } else {
            // Nếu insert thất bại (duplicate), query lại để return existing record
            // Điều này có thể xảy ra nếu có race condition giữa check và insert
            return aiEmbeddingRepository.findByContentAndNotDeleted(content)
                    .orElseThrow(() -> new RuntimeException("Failed to save embedding and cannot find existing record"));
        }
    }
    
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            System.err.println("Error converting map to JSON: " + e.getMessage());
            return "{}";
        }
    }

    @Override
    public List<AiEmbedding> findSimilarEmbeddings(String queryEmbedding, int limit) {
        // Log the SQL query
        String sqlQuery = String.format(
            "SELECT * FROM ai_embedding WHERE is_deleted = 0 ORDER BY embedding <=> CAST('%s' AS vector) LIMIT %d",
            queryEmbedding.length() > 100 ? queryEmbedding.substring(0, 100) + "..." : queryEmbedding,
            limit
        );
        
        VECTOR_SEARCH_LOGGER.info("\n" + "=".repeat(120));
        VECTOR_SEARCH_LOGGER.info("🔍 VECTOR SEARCH QUERY");
        VECTOR_SEARCH_LOGGER.info("=".repeat(120));
        VECTOR_SEARCH_LOGGER.info("📊 SQL Query:");
        VECTOR_SEARCH_LOGGER.info("   {}", sqlQuery);
        VECTOR_SEARCH_LOGGER.info("   Embedding dimensions: {} characters", queryEmbedding.length());
        VECTOR_SEARCH_LOGGER.info("   Requested limit: {}", limit);
        
        List<AiEmbedding> results = aiEmbeddingRepository.findSimilarEmbeddings(queryEmbedding, limit);
        
        VECTOR_SEARCH_LOGGER.info("\n✅ Results returned: {}", results.size());
        
        // Log vector search results
        if (!results.isEmpty()) {
            VECTOR_SEARCH_LOGGER.info("\n📋 DETAILED RESULTS:");
            VECTOR_SEARCH_LOGGER.info("-".repeat(120));
            
            for (int i = 0; i < results.size(); i++) {
                AiEmbedding emb = results.get(i);
                String question = null;
                String sourceFile = null;
                
                if (emb.getMetadata() != null) {
                    Object q = emb.getMetadata().get("question");
                    question = q != null ? q.toString() : null;
                    Object sf = emb.getMetadata().get("source_file");
                    sourceFile = sf != null ? sf.toString() : null;
                }
                
                String questionDisplay = question != null ? 
                    (question.length() > 80 ? question.substring(0, 80) + "..." : question) : 
                    "N/A";
                
                VECTOR_SEARCH_LOGGER.info("#{} | {} | Source: {}", 
                    i + 1, 
                    questionDisplay,
                    sourceFile != null ? sourceFile : "N/A"
                );
            }
            
            VECTOR_SEARCH_LOGGER.info("-".repeat(120));
        } else {
            VECTOR_SEARCH_LOGGER.warn("\n⚠️ No results found!");
            VECTOR_SEARCH_LOGGER.warn("   - Check if ai_embedding table has data");
            VECTOR_SEARCH_LOGGER.warn("   - Check if is_deleted = 0");
            VECTOR_SEARCH_LOGGER.warn("   - Check if embedding model is working correctly");
        }
        
        VECTOR_SEARCH_LOGGER.info("=".repeat(120) + "\n");
        
        return results;
    }

    @Override
    public boolean existsByContent(String content) {
        return aiEmbeddingRepository.findByContentAndNotDeleted(content).isPresent();
    }

    @Override
    public long countAllNotDeleted() {
        return aiEmbeddingRepository.findAllNotDeleted().size();
    }

    @Override
    public long countBySourceFile(String sourceFile) {
        return aiEmbeddingRepository.countBySourceFile(sourceFile);
    }
}
