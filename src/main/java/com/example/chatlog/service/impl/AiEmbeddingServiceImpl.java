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

    @Override
    public long countBySourceFile(String sourceFile) {
        return aiEmbeddingRepository.countBySourceFile(sourceFile);
    }

    @Override
    public List<AiEmbedding> fullTextSearch(String searchTerm, int limit) {
        System.out.println("🔍 Full-text search for: " + searchTerm);
        return aiEmbeddingRepository.fullTextSearch(searchTerm, limit);
    }

    @Override
    public List<AiEmbedding> hybridSearch(String queryEmbedding, String searchTerm, int limit) {
        System.out.println("🔍 Hybrid search for: " + searchTerm);
        
        // Pool rộng hơn để đảm bảo có kết quả keyword
        int vectorLimit = Math.max(50, limit * 2);
        int keywordLimit = Math.max(50, limit * 2);
        
        return aiEmbeddingRepository.hybridSearch(
            queryEmbedding, 
            searchTerm, 
            vectorLimit, 
            keywordLimit, 
            limit
        );
    }

    @Override
    public List<java.util.Map<String, Object>> hybridSearchDebug(String queryEmbedding, String searchTerm, int limit) {
        int vectorLimit = Math.max(50, limit * 2);
        int keywordLimit = Math.max(50, limit * 2);
        List<Object[]> rows = aiEmbeddingRepository.hybridSearchDebug(
            queryEmbedding, searchTerm, vectorLimit, keywordLimit, limit
        );
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", r[0]);
            m.put("content", r[1]);
            m.put("metadata", r[2]);
            m.put("similarity_score", r[3]);
            m.put("keyword_score", r[4]);
            m.put("final_score", r[5]);
            out.add(m);
        }
        // Print debug to console
        System.out.println("\n===== HYBRID SCORE DEBUG =====");
        for (int i = 0; i < out.size(); i++) {
            java.util.Map<String, Object> m = out.get(i);
            String question = null;
            try {
                // metadata may be Map already from entity; from native it's JSON string
                Object meta = m.get("metadata");
                if (meta instanceof java.util.Map) {
                    Object q = ((java.util.Map<?, ?>) meta).get("question");
                    question = q != null ? q.toString() : null;
                } else if (meta != null) {
                    question = meta.toString();
                }
            } catch (Exception ignore) {}
            System.out.println(String.format(
                "#%d final=%.4f (semantic=%.4f, keyword=%.4f) | %s",
                i + 1,
                toDouble(m.get("final_score")),
                toDouble(m.get("similarity_score")),
                toDouble(m.get("keyword_score")),
                question != null ? (question.length() > 80 ? question.substring(0, 80) + "..." : question) : ""
            ));
        }
        System.out.println("==============================\n");
        return out;
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }
}
