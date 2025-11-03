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

    // Đếm số embeddings theo source file
    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM ai_embedding a WHERE a.metadata->>'source_file' = ?1 AND a.is_deleted = 0")
    long countBySourceFile(String sourceFile);

    // Vector similarity search - Native SQL vì HQL không support vector operators
    @Query(nativeQuery = true, value = "SELECT * FROM ai_embedding WHERE is_deleted = 0 ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit")
    List<AiEmbedding> findSimilarEmbeddings(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
    
    /**
     * Full-text search trên content và metadata
     */
    @Query(value = """
        SELECT * FROM ai_embedding 
        WHERE is_deleted = 0 
        AND (
            LOWER(content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(CAST(metadata AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        )
        ORDER BY 
            CASE 
                WHEN LOWER(content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) THEN 1
                ELSE 2
            END
        LIMIT :limit
        """, nativeQuery = true)
    List<AiEmbedding> fullTextSearch(
        @Param("searchTerm") String searchTerm, 
        @Param("limit") int limit
    );
    
    /**
     * Hybrid search: Kết hợp vector similarity và keyword matching
     */
    @Query(value = """
        WITH vector_results AS (
            SELECT 
                id,
                content,
                embedding,
                metadata,
                created_at,
                updated_at,
                is_deleted,
                (1 - (embedding <=> CAST(:queryEmbedding AS vector))) AS similarity_score
            FROM ai_embedding
            WHERE is_deleted = 0
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :vectorLimit
        ),
        keyword_results AS (
            SELECT 
                id,
                content,
                embedding,
                metadata,
                created_at,
                updated_at,
                is_deleted,
                0.8 AS keyword_score
            FROM ai_embedding
            WHERE is_deleted = 0
            AND (
                LOWER(content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                OR LOWER(CAST(metadata AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            )
            LIMIT :keywordLimit
        )
        SELECT DISTINCT
            COALESCE(v.id, k.id) as id,
            COALESCE(v.content, k.content) as content,
            COALESCE(v.embedding, k.embedding) as embedding,
            COALESCE(v.metadata, k.metadata) as metadata,
            COALESCE(v.created_at, k.created_at) as created_at,
            COALESCE(v.updated_at, k.updated_at) as updated_at,
            COALESCE(v.is_deleted, k.is_deleted) as is_deleted,
            (COALESCE(v.similarity_score, 0) * 0.7 + COALESCE(k.keyword_score, 0) * 0.3) as final_score
        FROM vector_results v
        FULL OUTER JOIN keyword_results k ON v.id = k.id
        ORDER BY final_score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AiEmbedding> hybridSearch(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("searchTerm") String searchTerm,
        @Param("vectorLimit") int vectorLimit,
        @Param("keywordLimit") int keywordLimit,
        @Param("limit") int limit
    );

    /**
     * Hybrid search (debug): trả về điểm số để log (similarity_score, keyword_score, final_score)
     */
    @Query(value = """
        WITH vector_results AS (
            SELECT 
                id,
                content,
                embedding,
                metadata,
                created_at,
                updated_at,
                is_deleted,
                (1 - (embedding <=> CAST(:queryEmbedding AS vector))) AS similarity_score
            FROM ai_embedding
            WHERE is_deleted = 0
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :vectorLimit
        ),
        keyword_results AS (
            SELECT 
                id,
                content,
                embedding,
                metadata,
                created_at,
                updated_at,
                is_deleted,
                0.8 AS keyword_score
            FROM ai_embedding
            WHERE is_deleted = 0
            AND (
                LOWER(content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                OR LOWER(CAST(metadata AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            )
            LIMIT :keywordLimit
        )
        SELECT DISTINCT
            COALESCE(v.id, k.id) as id,
            COALESCE(v.content, k.content) as content,
            COALESCE(v.metadata, k.metadata) as metadata,
            COALESCE(v.similarity_score, 0) as similarity_score,
            COALESCE(k.keyword_score, 0) as keyword_score,
            (COALESCE(v.similarity_score, 0) * 0.7 + COALESCE(k.keyword_score, 0) * 0.3) as final_score
        FROM vector_results v
        FULL OUTER JOIN keyword_results k ON v.id = k.id
        ORDER BY final_score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> hybridSearchDebug(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("searchTerm") String searchTerm,
        @Param("vectorLimit") int vectorLimit,
        @Param("keywordLimit") int keywordLimit,
        @Param("limit") int limit
    );
    
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
