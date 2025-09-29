package com.example.chatlog.enums;

/**
 * Enum để quản lý các AI model provider
 */
public enum ModelProvider {
  OPENAI("OpenAI", "gpt-4o-mini"),
  OPENROUTER("OpenRouter", "x-ai/grok-4-fast:free");

  private final String displayName;
  private final String modelName;

  ModelProvider(String displayName, String modelName) {
    this.displayName = displayName;
    this.modelName = modelName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getModelName() {
    return modelName;
  }

  @Override
  public String toString() {
    return displayName + " (" + modelName + ")";
  }
}
