package com.example.chatlog.service;

import com.example.chatlog.entity.ai.AiEmbedding;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiEmbeddingService {

    // Lưu embedding vào database
    AiEmbedding saveEmbedding(String content, String embedding, Map<String, Object> metadata);

    // Tìm embeddings tương tự
    List<AiEmbedding> findSimilarEmbeddings(String queryEmbedding, int limit);

    // Kiểm tra xem embedding có tồn tại không
    boolean existsByContent(String content);

    // Đếm tất cả embeddings chưa xóa
    long countAllNotDeleted();

    // Đếm số embeddings theo source file
    long countBySourceFile(String sourceFile);

}
