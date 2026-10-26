package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ai.demo.client.gemini.GeminiClient;
import ai.demo.client.groq.GroqClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.ollama.OllamaClient;
import ai.demo.client.openai.OpenAiClient;
import ai.demo.config.AppConfig;
import ai.demo.config.GeminiConfig;
import ai.demo.config.GroqConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.config.OpenAiConfig;
import ai.demo.config.TestAppConfig;
import ai.demo.exception.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LlmClientFactoryTest {

  @Test
  void shouldCreateConfiguredProvider() {
    var factory =
        new LlmClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> "secret");

    assertInstanceOf(OllamaClient.class, factory.create(ollamaConfig()));
    assertInstanceOf(OpenAiClient.class, factory.create(openAiConfig()));
    assertInstanceOf(GroqClient.class, factory.create(cloudConfig(), LlmProvider.GROQ));
    assertInstanceOf(GeminiClient.class, factory.create(cloudConfig(), LlmProvider.GEMINI));
  }

  private AppConfig cloudConfig() {
    return TestAppConfig.builder(LlmProvider.GROQ)
        .groq(new GroqConfig("groq-model", "https://api.groq.com/openai/v1", "GROQ_API_KEY"))
        .gemini(
            new GeminiConfig(
                "gemini-model",
                "https://generativelanguage.googleapis.com/v1beta",
                "GEMINI_API_KEY"))
        .build();
  }

  @Test
  void shouldRejectMissingOpenAiApiKey() {
    var factory = new LlmClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> null);
    AppConfig config = openAiConfig();

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> factory.create(config));

    assertTrue(exception.getMessage().contains("$env:OPENAI_API_KEY='your-api-key'"));
  }

  @Test
  void shouldRejectDisabledOllamaProvider() {
    var factory =
        new LlmClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> "secret");
    AppConfig config =
        TestAppConfig.builder(LlmProvider.OPENAI)
            .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.18, false))
            .openAi(
                new OpenAiConfig("test-model", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
            .build();

    assertThrows(ConfigurationException.class, () -> factory.create(config, LlmProvider.OLLAMA));
  }

  @Test
  void shouldRejectUnconfiguredProvider() {
    var factory =
        new LlmClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> "secret");
    AppConfig config = ollamaConfig();

    assertThrows(ConfigurationException.class, () -> factory.create(config, LlmProvider.GROQ));
  }

  private AppConfig ollamaConfig() {
    return TestAppConfig.builder(LlmProvider.OLLAMA)
        .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.18))
        .build();
  }

  private AppConfig openAiConfig() {
    return TestAppConfig.builder(LlmProvider.OPENAI)
        .openAi(new OpenAiConfig("test-model", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
        .build();
  }
}
