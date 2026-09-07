package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ai.demo.client.gemini.GeminiEmbeddingClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.ollama.OllamaEmbeddingClient;
import ai.demo.client.openai.OpenAiEmbeddingClient;
import ai.demo.config.EmbeddingProvider;
import ai.demo.config.RagConfig;
import ai.demo.exception.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EmbeddingClientFactoryTest {

  @Test
  void shouldCreateOllamaClientWithoutRequiringApiKey() {
    var factory =
        new EmbeddingClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> null);

    assertInstanceOf(
        OllamaEmbeddingClient.class, factory.create(config(EmbeddingProvider.OLLAMA, null)));
  }

  @Test
  void shouldCreateOpenAiClientWhenApiKeyPresent() {
    var factory =
        new EmbeddingClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> "secret");

    assertInstanceOf(
        OpenAiEmbeddingClient.class,
        factory.create(config(EmbeddingProvider.OPENAI, "OPENAI_API_KEY")));
  }

  @Test
  void shouldCreateGeminiClientWhenApiKeyPresent() {
    var factory =
        new EmbeddingClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> "secret");

    assertInstanceOf(
        GeminiEmbeddingClient.class,
        factory.create(config(EmbeddingProvider.GEMINI, "GEMINI_API_KEY")));
  }

  @Test
  void shouldRejectMissingApiKeyForOpenAi() {
    var factory =
        new EmbeddingClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> null);
    RagConfig config = config(EmbeddingProvider.OPENAI, "OPENAI_API_KEY");

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> factory.create(config));

    assertTrue(exception.getMessage().contains("OPENAI_API_KEY"));
  }

  @Test
  void shouldRejectMissingApiKeyForGemini() {
    var factory =
        new EmbeddingClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> null);
    RagConfig config = config(EmbeddingProvider.GEMINI, "GEMINI_API_KEY");

    assertThrows(ConfigurationException.class, () -> factory.create(config));
  }

  private RagConfig config(EmbeddingProvider provider, String apiKeyEnv) {
    String baseUrl =
        switch (provider) {
          case OLLAMA -> "http://localhost:11434";
          case OPENAI -> "https://api.openai.com/v1";
          case GEMINI -> "https://generativelanguage.googleapis.com/v1beta";
        };
    return new RagConfig(
        true, provider, baseUrl, "model", apiKeyEnv, 200000, 20, 400000, 1200, 6000, 4000, 5, 0.2);
  }
}
