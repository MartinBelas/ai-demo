package ai.demo.client;

import ai.demo.client.gemini.GeminiEmbeddingClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.ollama.OllamaEmbeddingClient;
import ai.demo.client.openai.OpenAiEmbeddingClient;
import ai.demo.config.RagConfig;
import ai.demo.exception.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.UnaryOperator;

/** Creates the configured RAG embedding adapter, independent of the selected chat provider. */
public final class EmbeddingClientFactory {

  private final HttpTransport transport;
  private final ObjectMapper objectMapper;
  private final UnaryOperator<String> environment;

  public EmbeddingClientFactory(
      HttpTransport transport, ObjectMapper objectMapper, UnaryOperator<String> environment) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.environment = environment;
  }

  public EmbeddingClient create(RagConfig config) {
    return switch (config.provider()) {
      case OLLAMA -> new OllamaEmbeddingClient(transport, objectMapper, config);
      case OPENAI ->
          new OpenAiEmbeddingClient(config, requiredApiKey(config), transport, objectMapper);
      case GEMINI ->
          new GeminiEmbeddingClient(config, requiredApiKey(config), transport, objectMapper);
    };
  }

  private String requiredApiKey(RagConfig config) {
    String variable = config.apiKeyEnvironmentVariable();
    String apiKey = environment.apply(variable);
    if (apiKey == null || apiKey.isBlank()) {
      throw new ConfigurationException(
          "Environment variable '"
              + variable
              + "' is required for RAG embedding provider "
              + config.provider()
              + ". Add it to .env or set it before starting the application; in PowerShell use:"
              + " $env:"
              + variable
              + "='your-api-key'");
    }
    return apiKey;
  }
}
