package com.example.chatlog.service.impl;

import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.service.ModelConfigService;
import org.springframework.stereotype.Service;

/**
 * Implementation của ModelConfigService
 * Sử dụng in-memory storage để lưu trữ preferences
 */
@Service
public class ModelConfigServiceImpl implements ModelConfigService {

  private ModelProvider queryGenerationProvider = ModelProvider.OPENAI; // Mặc định
  private ModelProvider responseGenerationProvider = ModelProvider.OPENROUTER; // Mặc định

  @Override
  public ModelProvider getQueryGenerationProvider() {
    return queryGenerationProvider;
  }

  @Override
  public ModelProvider getResponseGenerationProvider() {
    return responseGenerationProvider;
  }

  @Override
  public void setQueryGenerationProvider(ModelProvider provider) {
    this.queryGenerationProvider = provider;
    System.out.println("[ModelConfigService] Query generation provider changed to: " + provider);
  }

  @Override
  public void setResponseGenerationProvider(ModelProvider provider) {
    this.responseGenerationProvider = provider;
    System.out.println("[ModelConfigService] Response generation provider changed to: " + provider);
  }

  @Override
  public void resetToDefault() {
    this.queryGenerationProvider = ModelProvider.OPENAI;
    this.responseGenerationProvider = ModelProvider.OPENROUTER;
    System.out.println("[ModelConfigService] Reset to default configuration");
  }
}
