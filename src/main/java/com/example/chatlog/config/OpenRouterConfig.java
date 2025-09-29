package com.example.chatlog.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình cho OpenRouter API
 * OpenRouter là dịch vụ cho phép truy cập nhiều model AI khác nhau thông qua một API thống nhất
 */
@Configuration
public class OpenRouterConfig {

  @Value("${spring.ai.openrouter.api-key}")
  private String openRouterApiKey;

  @Value("${spring.ai.openrouter.base-url}")
  private String openRouterBaseUrl;

  @Value("${spring.ai.openrouter.chat.options.model}")
  private String openRouterModel;

  @Value("${spring.ai.openrouter.referer:https://localhost:8080}")
  private String refererUrl;

  /**
   * Tạo RestClient được cấu hình để kết nối với OpenRouter API
   * @return RestClient được cấu hình cho OpenRouter
   */
  @Bean(name = "openRouterRestClient")
  public RestClient openRouterRestClient() {
    return RestClient.builder()
        .baseUrl(openRouterBaseUrl)
        .defaultHeader("Authorization", "Bearer " + openRouterApiKey)
        .defaultHeader("HTTP-Referer", refererUrl) // Yêu cầu bởi OpenRouter - có thể cấu hình
        .defaultHeader("X-Title", "HPT.AI") // Tùy chọn, giúp nhận diện ứng dụng
        .build();
  }

  /**
   * Tạo ChatClient cho OpenRouter bằng cách sử dụng default ChatClient builder
   * Sẽ được cấu hình thủ công trong AiServiceImpl để sử dụng RestClient
   */
  @Bean(name = "openRouterChatClient")
  public RestClient openRouterClient() {
    return openRouterRestClient();
  }
}