package ai.demo.model.chat;

import ai.demo.client.TokenUsage;

public record ChatResponse(
    String answer,
    String model,
    TokenUsage tokenUsage,
    long durationMs,
    java.util.List<ai.demo.model.rag.RagSource> sources,
    java.util.List<WebSource> webSources) {
  public ChatResponse(String answer, String model, TokenUsage tokenUsage, long durationMs) {
    this(answer, model, tokenUsage, durationMs, java.util.List.of());
  }

  public ChatResponse(
      String answer,
      String model,
      TokenUsage tokenUsage,
      long durationMs,
      java.util.List<ai.demo.model.rag.RagSource> sources) {
    this(answer, model, tokenUsage, durationMs, sources, java.util.List.of());
  }

  public ChatResponse {
    sources = java.util.List.copyOf(sources);
    webSources = java.util.List.copyOf(webSources);

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
