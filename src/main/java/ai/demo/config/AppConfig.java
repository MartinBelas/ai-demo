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
    ServerConfig server) {

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
        new ServerConfig(8080));
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

    if (provider == null) throw new IllegalArgumentException("provider must not be null");
    if (generation == null) throw new IllegalArgumentException("generation must not be null");
    if (provider == LlmProvider.OLLAMA && ollama == null) {
      throw new IllegalArgumentException("ollama configuration is required for provider OLLAMA");
    }
    if (provider == LlmProvider.OPENAI && openAi == null) {
      throw new IllegalArgumentException("openAi configuration is required for provider OPENAI");
    }
    if (provider == LlmProvider.GROQ && groq == null) {
      throw new IllegalArgumentException("groq configuration is required for provider GROQ");
    }
    if (provider == LlmProvider.GEMINI && gemini == null) {
      throw new IllegalArgumentException("gemini configuration is required for provider GEMINI");
    }
    if (conversationFile == null) {
      throw new IllegalArgumentException("conversationFile must not be null");
    }
    if (appInterface == null) {
      throw new IllegalArgumentException("appInterface must not be null");
    }
    if (server == null) {
      throw new IllegalArgumentException("server must not be null");
    }
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
}
