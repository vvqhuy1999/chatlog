package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ai.AiEmbedding;
import com.example.chatlog.repository.AiEmbeddingRepository;
import com.example.chatlog.service.AiEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("secondaryTransactionManager")  // CHỈ ĐỊNH RÕ TRANSACTION MANAGER PHỤ
public class AiEmbeddingServiceImpl implements AiEmbeddingService {

    private final AiEmbeddingRepository aiEmbeddingRepository;

    @Override
    public AiEmbedding saveEmbedding(String content, String embedding, Map<String, Object> metadata) {
        // Generate ID and timestamps manually (since @PrePersist won't be triggered)
        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        
        // Convert metadata Map to JSON String
        String metadataJson = convertMapToJson(metadata);
        
        // Use custom native query with explicit vector cast
        aiEmbeddingRepository.saveWithVectorCast(
            id,
            content,
            embedding,
            metadataJson,
            now,  // createdAt
            now,  // updatedAt
            0     // isDeleted
        );
        
        // Return the saved entity (build it for return)
        return AiEmbedding.builder()
                .id(id)
                .content(content)
                .embedding(embedding)
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();
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
    public AiEmbedding findByContent(String content) {
        return aiEmbeddingRepository.findByContentAndNotDeleted(content).orElse(null);
    }

    @Override
    public List<AiEmbedding> getAllNotDeleted() {
        return aiEmbeddingRepository.findAllNotDeleted();
    }

    @Override
    public List<AiEmbedding> findSimilarEmbeddings(String queryEmbedding, int limit) {
        return aiEmbeddingRepository.findSimilarEmbeddings(queryEmbedding, limit);
    }

    @Override
    public void softDeleteEmbedding(UUID id) {
        aiEmbeddingRepository.findById(id).ifPresent(embedding -> {
            embedding.setIsDeleted(1);
            aiEmbeddingRepository.save(embedding);
        });
    }

    @Override
    public void deleteBySourceFile(String sourceFile) {
        List<AiEmbedding> embeddings = aiEmbeddingRepository.findBySourceFile(sourceFile);
        embeddings.forEach(embedding -> embedding.setIsDeleted(1));
        aiEmbeddingRepository.saveAll(embeddings);
    }

    @Override
    public List<AiEmbedding> getBySourceFile(String sourceFile) {
        return aiEmbeddingRepository.findBySourceFile(sourceFile);
    }

    @Override
    public boolean existsByContent(String content) {
        return aiEmbeddingRepository.findByContentAndNotDeleted(content).isPresent();
    }

    @Override
    public long countAllNotDeleted() {
        return aiEmbeddingRepository.findAllNotDeleted().size();
    }
}
