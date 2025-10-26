package com.example.chatlog.repository;

import com.example.chatlog.entity.ai.AiEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiEmbeddingRepository extends JpaRepository<AiEmbedding, UUID> {

    // Tìm embedding theo content
    @Query("SELECT a FROM AiEmbedding a WHERE a.content = ?1 AND a.isDeleted = 0")
    Optional<AiEmbedding> findByContentAndNotDeleted(String content);

    // Tìm tất cả embedding chưa xóa
    @Query("SELECT a FROM AiEmbedding a WHERE a.isDeleted = 0 ORDER BY a.createdAt DESC")
    List<AiEmbedding> findAllNotDeleted();

    // Tìm embeddings theo source file trong metadata
    @Query(nativeQuery = true, value = "SELECT * FROM ai_embedding a WHERE a.metadata->>'source_file' = ?1 AND a.is_deleted = 0")
    List<AiEmbedding> findBySourceFile(String sourceFile);

    // Vector similarity search - Native SQL vì HQL không support vector operators
    @Query(nativeQuery = true, value = "SELECT * FROM ai_embedding WHERE is_deleted = 0 ORDER BY embedding <=> ?1::vector LIMIT ?2")
    List<AiEmbedding> findSimilarEmbeddings(String queryEmbedding, int limit);
}
