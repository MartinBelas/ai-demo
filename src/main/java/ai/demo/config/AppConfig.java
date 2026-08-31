package ai.demo.config;

import java.nio.file.Path;

/** Provider-independent application configuration. */
public record AppConfig(
    LlmProvider provider,
    GenerationConfig generation,
    OllamaConfig ollama,
    OpenAiConfig openAi,
    GroqConfig groq,
    GeminiConfig gemini,
    Path conversationFile,
    AppInterface appInterface,
    ServerConfig server,
    DemoLimitsConfig demoLimits) {

  public AppConfig(
      LlmProvider provider,
      GenerationConfig generation,
      OllamaConfig ollama,
      OpenAiConfig openAi,
      GroqConfig groq,
      GeminiConfig gemini,
      Path conversationFile) {
    this(
        provider,
        generation,
        ollama,
        openAi,
        groq,
        gemini,
        conversationFile,
        AppInterface.CONSOLE,
        new ServerConfig(8080),
        DemoLimitsConfig.disabled());
  }

  public AppConfig(
      LlmProvider provider,
      GenerationConfig generation,
      OllamaConfig ollama,
      OpenAiConfig openAi,
      Path conversationFile) {
    this(provider, generation, ollama, openAi, null, null, conversationFile);
  }

  public AppConfig {
    requireNonNull(provider, "provider");
    requireNonNull(generation, "generation");
    requireNonNull(conversationFile, "conversationFile");
    requireNonNull(appInterface, "appInterface");
    requireNonNull(server, "server");
    requireNonNull(demoLimits, "demoLimits");
    validateProviderConfiguration(provider, ollama, openAi, groq, gemini);
    validateOutputLimit(generation, demoLimits);
  }

  public String model() {
    return model(provider);
  }

  public String model(LlmProvider selectedProvider) {
    return switch (selectedProvider) {
      case OLLAMA -> ollama.model();
      case OPENAI -> openAi.model();
      case GROQ -> groq.model();
      case GEMINI -> gemini.model();
    };
  }

  private static void requireNonNull(Object value, String name) {
    if (value == null) throw new IllegalArgumentException(name + " must not be null");
  }

  private static void validateProviderConfiguration(
      LlmProvider provider,
      OllamaConfig ollama,
      OpenAiConfig openAi,
      GroqConfig groq,
      GeminiConfig gemini) {
    switch (provider) {
      case OLLAMA -> validateOllama(ollama);
      case OPENAI -> requireNonNull(openAi, "openAi configuration");
      case GROQ -> requireNonNull(groq, "groq configuration");
      case GEMINI -> requireNonNull(gemini, "gemini configuration");
    }
  }

  private static void validateOllama(OllamaConfig ollama) {
    requireNonNull(ollama, "ollama configuration");
    if (!ollama.enabled()) {
      throw new IllegalArgumentException("provider OLLAMA must be enabled when selected");
    }
  }

  private static void validateOutputLimit(
      GenerationConfig generation, DemoLimitsConfig demoLimits) {
    if (demoLimits.enabled()
        && generation.maxOutputTokens() > demoLimits.maxOutputTokensPerCall()) {
      throw new IllegalArgumentException(
          "maxOutputTokens must not exceed the public demo per-call limit");
    }
  }
}
