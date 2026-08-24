package ai.demo.config;

public record OllamaConfig(String model, String baseUrl, int contextWindow, double repeatPenalty) {
  public OllamaConfig {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Ollama model must not be blank");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Ollama baseUrl must not be blank");
    }
    if (contextWindow < 1) throw new IllegalArgumentException("contextWindow must be positive");
    if (repeatPenalty < 1.0) {
      throw new IllegalArgumentException("repeatPenalty must be at least 1.0");
    }
  }
}
