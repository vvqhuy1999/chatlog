package com.example.chatlog.service;

import com.example.chatlog.entity.ai.AiEmbedding;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiEmbeddingService {

    // Lưu embedding vào database
    AiEmbedding saveEmbedding(String content, String embedding, Map<String, Object> metadata);

    // Tìm embedding theo content
    AiEmbedding findByContent(String content);

    // Lấu tất cả embedding chưa xóa
    List<AiEmbedding> getAllNotDeleted();

    // Tìm embeddings tương tự
    List<AiEmbedding> findSimilarEmbeddings(String queryEmbedding, int limit);

    // Soft delete embedding
    void softDeleteEmbedding(UUID id);

    // Xóa tất cả embeddings của một source file
    void deleteBySourceFile(String sourceFile);

    // Lấy tất cả embeddings theo source file
    List<AiEmbedding> getBySourceFile(String sourceFile);

    // Kiểm tra xem embedding có tồn tại không
    boolean existsByContent(String content);

    // Đếm tất cả embeddings chưa xóa
    long countAllNotDeleted();

    // Đếm số embeddings theo source file
    long countBySourceFile(String sourceFile);
}
