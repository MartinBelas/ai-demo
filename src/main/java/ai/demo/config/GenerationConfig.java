package ai.demo.config;

public record GenerationConfig(double temperature, int maxOutputTokens, String systemMessage) {
  public GenerationConfig {
    if (temperature < 0.0 || temperature > 2.0) {
      throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
    }
    if (maxOutputTokens < 1) {
      throw new IllegalArgumentException("maxOutputTokens must be positive");
    }
    if (systemMessage == null || systemMessage.isBlank()) {
      throw new IllegalArgumentException("systemMessage must not be blank");
    }
  }
}
