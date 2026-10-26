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
    DemoLimitsConfig demoLimits,
    RagConfig rag,
    AgentLabConfig agentLab) {

  public AppConfig {
    requireNonNull(provider, "provider");
    requireNonNull(generation, "generation");
    requireNonNull(conversationFile, "conversationFile");
    requireNonNull(appInterface, "appInterface");
    requireNonNull(server, "server");
    requireNonNull(demoLimits, "demoLimits");
    requireNonNull(rag, "rag");
    requireNonNull(agentLab, "agentLab");
    validateProviderConfiguration(provider, ollama, openAi, groq, gemini);
    validateOutputLimit(generation, demoLimits);
    validateEmbeddingProvider(rag, ollama);
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

  private static void validateEmbeddingProvider(RagConfig rag, OllamaConfig ollama) {
    // Only an explicit "ollama.enabled=false" counts as Ollama being unavailable; a missing
    // ollama section just means no local chat model is configured.
    if (rag.enabled()
        && rag.provider() == EmbeddingProvider.OLLAMA
        && ollama != null
        && !ollama.enabled()) {
      throw new IllegalArgumentException(
          "RAG embedding provider OLLAMA cannot be used while Ollama is disabled "
              + "(ollama.enabled=false); select OPENAI or GEMINI embeddings or disable RAG");
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
