package com.example.chatlog.service;

import com.example.chatlog.enums.ModelProvider;

/**
 * Service để quản lý cấu hình model
 */
public interface ModelConfigService {

  /**
   * Lấy model provider hiện tại cho việc tạo query
   * @return ModelProvider cho query generation
   */
  ModelProvider getQueryGenerationProvider();

  /**
   * Lấy model provider hiện tại cho việc tạo response
   * @return ModelProvider cho response generation
   */
  ModelProvider getResponseGenerationProvider();

  /**
   * Cập nhật model provider cho việc tạo query
   * @param provider ModelProvider mới
   */
  void setQueryGenerationProvider(ModelProvider provider);

  /**
   * Cập nhật model provider cho việc tạo response
   * @param provider ModelProvider mới
   */
  void setResponseGenerationProvider(ModelProvider provider);

  /**
   * Reset về cấu hình mặc định
   */
  void resetToDefault();
}
