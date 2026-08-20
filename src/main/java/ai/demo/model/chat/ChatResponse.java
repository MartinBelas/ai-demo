package ai.demo.model.chat;

import ai.demo.client.TokenUsage;

public record ChatResponse(String answer, String model, TokenUsage tokenUsage, long durationMs) {
  public ChatResponse {

    if (answer == null || answer.isBlank()) {
      throw new IllegalArgumentException("answer must not be blank");
    }

    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }

    if (tokenUsage == null) {
      throw new IllegalArgumentException("tokenUsage must not be null");
    }

    if (durationMs < 0) {
      throw new IllegalArgumentException("durationMs must not be negative");
    }
  }
}
