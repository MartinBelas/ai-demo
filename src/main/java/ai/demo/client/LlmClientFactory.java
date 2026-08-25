package ai.demo.client;

import ai.demo.client.gemini.GeminiClient;
import ai.demo.client.groq.GroqClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.ollama.OllamaClient;
import ai.demo.client.openai.OpenAiClient;
import ai.demo.config.AppConfig;
import ai.demo.config.LlmProvider;
import ai.demo.exception.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Creates the configured provider adapter without leaking provider selection into services. */
public final class LlmClientFactory {

  private final HttpTransport transport;
  private final ObjectMapper objectMapper;
  private final UnaryOperator<String> environment;

  public LlmClientFactory(
      HttpTransport transport, ObjectMapper objectMapper, UnaryOperator<String> environment) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.environment = environment;
  }

  public LlmClient create(AppConfig config) {
    return create(config, config.provider());
  }

  public LlmClient create(AppConfig config, LlmProvider provider) {
    return switch (provider) {
      case OLLAMA -> createOllama(config);
      case OPENAI -> createOpenAi(config);
      case GROQ -> createGroq(config);
      case GEMINI -> createGemini(config);
    };
  }

  public SwitchableLlmClient createSwitchable(AppConfig config) {
    EnumMap<LlmProvider, Supplier<LlmClient>> factories = new EnumMap<>(LlmProvider.class);
    if (config.ollama() != null && config.ollama().enabled()) {
      factories.put(LlmProvider.OLLAMA, () -> create(config, LlmProvider.OLLAMA));
    }
    if (config.openAi() != null) {
      factories.put(LlmProvider.OPENAI, () -> create(config, LlmProvider.OPENAI));
    }
    if (config.groq() != null) {
      factories.put(LlmProvider.GROQ, () -> create(config, LlmProvider.GROQ));
    }
    if (config.gemini() != null) {
      factories.put(LlmProvider.GEMINI, () -> create(config, LlmProvider.GEMINI));
    }
    return new SwitchableLlmClient(config.provider(), factories);
  }

  private LlmClient createOllama(AppConfig config) {
    if (config.ollama() == null || !config.ollama().enabled()) {
      throw new ConfigurationException("Provider OLLAMA is disabled");
    }
    return new OllamaClient(config, transport, objectMapper);
  }

  private LlmClient createGroq(AppConfig config) {
    String apiKey = requiredApiKey(config.groq().apiKeyEnvironmentVariable(), "GROQ");
    return new GroqClient(config, apiKey, transport, objectMapper);
  }

  private LlmClient createGemini(AppConfig config) {
    String apiKey = requiredApiKey(config.gemini().apiKeyEnvironmentVariable(), "GEMINI");
    return new GeminiClient(config, apiKey, transport, objectMapper);
  }

  private LlmClient createOpenAi(AppConfig config) {
    String variable = config.openAi().apiKeyEnvironmentVariable();
    String apiKey = requiredApiKey(variable, "OPENAI");
    return new OpenAiClient(config, apiKey, transport, objectMapper);
  }

  private String requiredApiKey(String variable, String provider) {
    String apiKey = environment.apply(variable);
    if (apiKey == null || apiKey.isBlank()) {
      throw new ConfigurationException(
          "Environment variable '"
              + variable
              + "' is required for provider "
              + provider
              + ". Add it to .env or set it before starting the application; in PowerShell use:"
              + " $env:"
              + variable
              + "='your-api-key'");
    }
    return apiKey;
  }
}
