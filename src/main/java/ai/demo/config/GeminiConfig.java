package ai.demo.config;

public record GeminiConfig(String model, String baseUrl, String apiKeyEnvironmentVariable) {

  public GeminiConfig {
    if (model == null || model.isBlank())
      throw new IllegalArgumentException("model must not be blank");
    if (baseUrl == null || baseUrl.isBlank())
      throw new IllegalArgumentException("baseUrl must not be blank");
    if (apiKeyEnvironmentVariable == null || apiKeyEnvironmentVariable.isBlank()) {
      throw new IllegalArgumentException("apiKeyEnvironmentVariable must not be blank");
    }
  }
}
