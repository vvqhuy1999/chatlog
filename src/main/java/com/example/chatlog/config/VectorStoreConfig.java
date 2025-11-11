package com.example.chatlog.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // Tạo SimpleVectorStore với EmbeddingModel
        // Vector Store này sẽ được sử dụng trong bộ nhớ làm cache
        // Embeddings chính được lưu trữ trong PostgreSQL/Supabase Database
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        
        System.out.println("✅ Vector Store initialized (in-memory with Database persistence)");
        System.out.println("   Embeddings will be persisted in: PostgreSQL/Supabase Database");
        System.out.println("   Vector Store is used as in-memory cache for fast lookup");
        
        // Trả về bean để các service khác có thể sử dụng
        return vectorStore;
    }
}
