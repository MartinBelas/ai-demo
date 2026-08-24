package ai.demo.config;

public record OpenAiConfig(String model, String baseUrl, String apiKeyEnvironmentVariable) {
  public OpenAiConfig {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("OpenAI model must not be blank");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("OpenAI baseUrl must not be blank");
    }
    if (apiKeyEnvironmentVariable == null || apiKeyEnvironmentVariable.isBlank()) {
      throw new IllegalArgumentException("OpenAI API key environment variable must not be blank");
    }
  }
}
