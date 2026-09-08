package ai.demo.client.openai;

/** Identity and capabilities of a provider served through the OpenAI-compatible Responses API. */
public record OpenAiCompatibleSettings(
    String providerName, String model, String baseUrl, boolean temperatureSupported) {
  public OpenAiCompatibleSettings {
    if (providerName == null || providerName.isBlank()) {
      throw new IllegalArgumentException("providerName must not be blank");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }
  }
}
