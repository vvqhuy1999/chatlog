package com.example.chatlog.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class VectorStoreConfig {

    // ⭐ Chuyển file vào resources folder
    private final File vectorStoreFile = new File("src/main/resources/vector_store.json");

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // Tạo SimpleVectorStore với EmbeddingModel
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // Nếu file đã tồn tại, tải dữ liệu từ file vào bộ nhớ khi khởi động
        if (this.vectorStoreFile.exists()) {
            System.out.println("✅ Tải Vector Store từ file: " + vectorStoreFile.getAbsolutePath());
            vectorStore.load(this.vectorStoreFile);
        } else {
            System.out.println("ℹ️ Không tìm thấy file Vector Store, sẽ tạo file mới sau khi indexing.");
        }
        
        // Trả về bean để các service khác có thể sử dụng
        return vectorStore;
    }
}
