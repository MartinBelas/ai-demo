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
import ai.demo.config.GenerationConfig;
import ai.demo.config.GroqConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.config.OpenAiConfig;
import ai.demo.exception.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
    return new AppConfig(
        LlmProvider.GROQ,
        new GenerationConfig(0.4, 300, "Be helpful."),
        null,
        null,
        new GroqConfig("groq-model", "https://api.groq.com/openai/v1", "GROQ_API_KEY"),
        new GeminiConfig(
            "gemini-model", "https://generativelanguage.googleapis.com/v1beta", "GEMINI_API_KEY"),
        Path.of("conversation.json"));
  }

  @Test
  void shouldRejectMissingOpenAiApiKey() {
    var factory = new LlmClientFactory(mock(HttpTransport.class), new ObjectMapper(), key -> null);

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> factory.create(openAiConfig()));

    assertTrue(exception.getMessage().contains("$env:OPENAI_API_KEY='your-api-key'"));
  }

  private AppConfig ollamaConfig() {
    return new AppConfig(
        LlmProvider.OLLAMA,
        new GenerationConfig(0.4, 300, "Be helpful."),
        new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.18),
        null,
        Path.of("conversation.json"));
  }

  private AppConfig openAiConfig() {
    return new AppConfig(
        LlmProvider.OPENAI,
        new GenerationConfig(0.4, 300, "Be helpful."),
        null,
        new OpenAiConfig("test-model", "https://api.openai.com/v1", "OPENAI_API_KEY"),
        Path.of("conversation.json"));
  }
}
