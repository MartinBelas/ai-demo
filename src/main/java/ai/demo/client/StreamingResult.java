package ai.demo.client;

public record StreamingResult(String model, TokenUsage tokenUsage) {
  public StreamingResult {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }
    if (tokenUsage == null) {
      throw new IllegalArgumentException("tokenUsage must not be null");
    }
  }
}
