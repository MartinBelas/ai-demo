package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  @Test
  void shouldCreateConfiguration() {

    var config =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(new GenerationConfig(0.5, 2000, "Be helpful."))
            .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2))
            .build();

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
    var withoutProvider =
        TestAppConfig.builder(null)
            .generation(generation)
            .ollama(ollama)
            .conversationFile(conversationFile);
    var withoutGeneration =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(null)
            .ollama(ollama)
            .conversationFile(conversationFile);
    var withoutOllama =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(generation)
            .conversationFile(conversationFile);
    var withoutConversationFile =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(generation)
            .ollama(ollama)
            .conversationFile(null);

    assertThrows(IllegalArgumentException.class, withoutProvider::build);
    assertThrows(IllegalArgumentException.class, withoutGeneration::build);
    assertThrows(IllegalArgumentException.class, withoutOllama::build);
    assertThrows(IllegalArgumentException.class, withoutConversationFile::build);
  }

  @Test
  void shouldRejectOllamaEmbeddingsWhenOllamaIsDisabled() {
    var disabledOllama = new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2, false);
    var rag = TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 200000);
    var builder =
        TestAppConfig.builder(LlmProvider.OPENAI)
            .openAi(new OpenAiConfig("gpt", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
            .ollama(disabledOllama)
            .rag(rag);

    var exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertTrue(exception.getMessage().contains("RAG embedding provider OLLAMA"));
  }

  @Test
  void shouldAllowOllamaEmbeddingsWhenOllamaIsEnabled() {
    var config =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2))
            .rag(TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 200000))
            .build();

    assertEquals(EmbeddingProvider.OLLAMA, config.rag().provider());
  }

  @Test
  void shouldAllowCloudEmbeddingsWhenOllamaIsDisabled() {
    var disabledOllama = new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2, false);

    var config =
        TestAppConfig.builder(LlmProvider.OPENAI)
            .openAi(new OpenAiConfig("gpt", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
            .ollama(disabledOllama)
            .rag(TestAppConfig.enabledRag(EmbeddingProvider.OPENAI, 200000))
            .build();

    assertEquals(EmbeddingProvider.OPENAI, config.rag().provider());
  }

  @Test
  void shouldAllowOllamaEmbeddingsWithoutOllamaSection() {
    var config =
        TestAppConfig.builder(LlmProvider.OPENAI)
            .openAi(new OpenAiConfig("gpt", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
            .rag(TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 200000))
            .build();

    assertEquals(EmbeddingProvider.OLLAMA, config.rag().provider());
  }
}
