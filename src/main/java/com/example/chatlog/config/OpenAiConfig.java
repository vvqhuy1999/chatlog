package com.example.chatlog.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cấu hình cho OpenAI API
 * Sử dụng Spring Boot auto-configuration cho OpenAI
 */
@Configuration
public class OpenAiConfig {

  /**
   * Tạo ChatClient cho OpenAI sử dụng auto-configuration
   * Spring Boot sẽ tự động cấu hình OpenAI client dựa trên application.yaml
   */
  @Bean(name = "openAiChatClient")
  @Primary // Đánh dấu là ChatClient mặc định
  public ChatClient openAiChatClient(ChatClient.Builder chatClientBuilder) {
    return chatClientBuilder
        .defaultSystem("You are a helpful AI assistant")
        .build();
  }
}