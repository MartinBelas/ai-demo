package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  @Test
  void shouldCreateConfiguration() {

    var config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.5, 2000, "Be helpful."),
            new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2),
            null,
            Path.of("conversation.json"));

    assertEquals(LlmProvider.OLLAMA, config.provider());
    assertEquals("http://localhost:11434", config.ollama().baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.5, config.generation().temperature());
    assertEquals(2000, config.generation().maxOutputTokens());
    assertEquals(4096, config.ollama().contextWindow());
    assertEquals(1.2, config.ollama().repeatPenalty());
    assertEquals(Path.of("conversation.json"), config.conversationFile());
  }

  @Test
  void shouldRejectInvalidConfiguration() {

    var generation = new GenerationConfig(0.5, 100, "Be helpful.");
    var ollama = new OllamaConfig("model", "url", 100, 1.2);
    Path conversationFile = Path.of("x");
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig(null, generation, ollama, null, conversationFile));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig(LlmProvider.OLLAMA, null, ollama, null, conversationFile));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig(LlmProvider.OLLAMA, generation, null, null, conversationFile));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig(LlmProvider.OLLAMA, generation, ollama, null, null));
  }
}
