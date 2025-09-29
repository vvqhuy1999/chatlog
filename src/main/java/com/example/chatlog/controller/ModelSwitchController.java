package com.example.chatlog.controller;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.enums.ModelProvider;
import com.example.chatlog.service.ModelConfigService;
import com.example.chatlog.service.impl.AiServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller để quản lý việc chuyển đổi giữa các model AI
 */
@RestController
@RequestMapping("/api/model")
public class ModelSwitchController {

  @Value("${spring.ai.openai.chat.options.model}")
  private String openAiModel;

  @Value("${spring.ai.openrouter.chat.options.model}")
  private String openRouterModel;

  @Autowired
  private ModelConfigService modelConfigService;

  @Autowired
  private AiServiceImpl aiServiceImpl;

  /**
   * Lấy thông tin về các model hiện có
   * @return Danh sách các model
   */
  @GetMapping("/info")
  public ResponseEntity<Map<String, Object>> getModelInfo() {
    Map<String, Object> models = new HashMap<>();
    models.put("openai", Map.of(
        "name", openAiModel,
        "provider", "OpenAI",
        "description", "OpenAI model"
    ));
    models.put("openrouter", Map.of(
        "name", openRouterModel,
        "provider", "OpenRouter",
        "description", "OpenRouter model"
    ));

    Map<String, Object> response = new HashMap<>();
    response.put("models", models);
    response.put("current_query_generation", modelConfigService.getQueryGenerationProvider().name().toLowerCase());
    response.put("current_response_generation", modelConfigService.getResponseGenerationProvider().name().toLowerCase());
    response.put("openrouter_model", openRouterModel);
    response.put("openai_model", openAiModel);

    return ResponseEntity.ok(response);
  }

  /**
   * API để kiểm tra trạng thái kết nối đến các model
   * @return Trạng thái kết nối
   */
  @GetMapping("/status")
  public ResponseEntity<Map<String, String>> getModelStatus() {
    Map<String, String> status = new HashMap<>();
    status.put("openai", "connected");
    status.put("openrouter", "connected");
    return ResponseEntity.ok(status);
  }

  /**
   * Chuyển đổi model cho việc tạo Elasticsearch query
   * @param provider Model provider (openai hoặc openrouter)
   * @return Kết quả chuyển đổi
   */
  @PostMapping("/switch/query/{provider}")
  public ResponseEntity<Map<String, Object>> switchQueryModel(@PathVariable String provider) {
    try {
      ModelProvider modelProvider = ModelProvider.valueOf(provider.toUpperCase());
      modelConfigService.setQueryGenerationProvider(modelProvider);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "Query generation model switched to " + modelProvider.getDisplayName());
      response.put("current_provider", modelProvider.name().toLowerCase());
      response.put("model_name", modelProvider.getModelName());

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", "Invalid provider: " + provider + ". Use 'openai' or 'openrouter'");
      return ResponseEntity.badRequest().body(response);
    }
  }

  /**
   * Chuyển đổi model cho việc tạo response
   * @param provider Model provider (openai hoặc openrouter)
   * @return Kết quả chuyển đổi
   */
  @PostMapping("/switch/response/{provider}")
  public ResponseEntity<Map<String, Object>> switchResponseModel(@PathVariable String provider) {
    try {
      ModelProvider modelProvider = ModelProvider.valueOf(provider.toUpperCase());
      modelConfigService.setResponseGenerationProvider(modelProvider);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "Response generation model switched to " + modelProvider.getDisplayName());
      response.put("current_provider", modelProvider.name().toLowerCase());
      response.put("model_name", modelProvider.getModelName());

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", "Invalid provider: " + provider + ". Use 'openai' or 'openrouter'");
      return ResponseEntity.badRequest().body(response);
    }
  }

  /**
   * Chuyển đổi cả hai model cùng lúc
   * @param provider Model provider (openai hoặc openrouter)
   * @return Kết quả chuyển đổi
   */
  @PostMapping("/switch/all/{provider}")
  public ResponseEntity<Map<String, Object>> switchAllModels(@PathVariable String provider) {
    try {
      ModelProvider modelProvider = ModelProvider.valueOf(provider.toUpperCase());
      modelConfigService.setQueryGenerationProvider(modelProvider);
      modelConfigService.setResponseGenerationProvider(modelProvider);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "Both query and response generation switched to " + modelProvider.getDisplayName());
      response.put("current_provider", modelProvider.name().toLowerCase());
      response.put("model_name", modelProvider.getModelName());

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", "Invalid provider: " + provider + ". Use 'openai' or 'openrouter'");
      return ResponseEntity.badRequest().body(response);
    }
  }

  /**
   * Reset về cấu hình mặc định
   * @return Kết quả reset
   */
  @PostMapping("/reset")
  public ResponseEntity<Map<String, Object>> resetToDefault() {
    modelConfigService.resetToDefault();

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "Reset to default configuration");
    response.put("query_provider", modelConfigService.getQueryGenerationProvider().name().toLowerCase());
    response.put("response_provider", modelConfigService.getResponseGenerationProvider().name().toLowerCase());

    return ResponseEntity.ok(response);
  }

  /**
   * API để so sánh cả 2 AI model cùng lúc
   * @param sessionId Session ID
   * @param chatRequest Câu hỏi từ user
   * @return Kết quả so sánh từ cả OpenAI và OpenRouter
   */
  @PostMapping("/compare/{sessionId}")
  public ResponseEntity<Map<String, Object>> compareModels(
      @PathVariable Long sessionId,
      @RequestBody ChatRequest chatRequest) {

    try {
      System.out.println("[ModelSwitchController] Starting comparison mode for question: " + chatRequest.message());

      Map<String, Object> comparisonResult = aiServiceImpl.handleRequestWithComparison(sessionId, chatRequest);

      // Thêm metadata
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("mode", "comparison");
      response.put("session_id", sessionId);
      response.putAll(comparisonResult);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      System.out.println("[ModelSwitchController] Comparison failed: " + e.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("error", "Comparison failed: " + e.getMessage());
      errorResponse.put("session_id", sessionId);

      return ResponseEntity.status(500).body(errorResponse);
    }
  }
}
