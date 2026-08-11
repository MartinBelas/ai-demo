package ai.demo.client;

public record LlmResponse(String text, String model, TokenUsage tokenUsage) {

  public LlmResponse {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("text must not be blank");
    }

    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }

    if (tokenUsage == null) {
      throw new IllegalArgumentException("tokenUsage must not be null");
    }
  }
}
