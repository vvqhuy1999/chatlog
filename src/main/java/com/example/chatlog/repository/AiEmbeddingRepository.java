package com.example.chatlog.repository;

import com.example.chatlog.entity.ai.AiEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    @Query(nativeQuery = true, value = "SELECT * FROM ai_embedding WHERE is_deleted = 0 ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit")
    List<AiEmbedding> findSimilarEmbeddings(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
    
    // Custom insert với explicit vector cast
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO ai_embedding (id, content, embedding, metadata, created_at, updated_at, is_deleted)
        VALUES (:id, :content, CAST(:embedding AS vector), CAST(:metadata AS jsonb), :createdAt, :updatedAt, :isDeleted)
        ON CONFLICT (id) DO UPDATE SET
            content = EXCLUDED.content,
            embedding = EXCLUDED.embedding,
            metadata = EXCLUDED.metadata,
            updated_at = EXCLUDED.updated_at,
            is_deleted = EXCLUDED.is_deleted
        """)
    void saveWithVectorCast(
        @Param("id") UUID id,
        @Param("content") String content,
        @Param("embedding") String embedding,
        @Param("metadata") String metadata,
        @Param("createdAt") OffsetDateTime createdAt,
        @Param("updatedAt") OffsetDateTime updatedAt,
        @Param("isDeleted") Integer isDeleted
    );
}
