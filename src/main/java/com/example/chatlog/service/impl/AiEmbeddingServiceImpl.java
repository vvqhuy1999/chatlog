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
@Transactional
public class AiEmbeddingServiceImpl implements AiEmbeddingService {

    private final AiEmbeddingRepository aiEmbeddingRepository;

    @Override
    public AiEmbedding saveEmbedding(String content, String embedding, Map<String, Object> metadata) {
        AiEmbedding aiEmbedding = AiEmbedding.builder()
                .content(content)
                .embedding(embedding)
                .metadata(metadata)
                .build();
        return aiEmbeddingRepository.save(aiEmbedding);
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

    /**
     * Convert float[] embedding to PostgreSQL vector format string
     * Example: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     */
    private String convertEmbeddingToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
