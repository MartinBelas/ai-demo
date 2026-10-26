package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RagConfigTest {

  @Test
  void shouldCreateDisabledConfigWithOllamaProvider() {
    RagConfig config = RagConfig.disabled();

    assertFalse(config.enabled());
  }

  @Test
  void shouldNotRequireApiKeyForOllama() {
    RagConfig config =
        assertDoesNotThrow(() -> valid(EmbeddingProvider.OLLAMA, "http://localhost:11434", null));

    assertEquals(EmbeddingProvider.OLLAMA, config.provider());
  }

  @Test
  void shouldRequireApiKeyEnvironmentVariableForOpenAi() {
    assertThrows(
        IllegalArgumentException.class,
        () -> valid(EmbeddingProvider.OPENAI, "https://api.openai.com/v1", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> valid(EmbeddingProvider.OPENAI, "https://api.openai.com/v1", " "));
  }

  @Test
  void shouldAcceptApiKeyEnvironmentVariableForOpenAi() {
    RagConfig config =
        assertDoesNotThrow(
            () -> valid(EmbeddingProvider.OPENAI, "https://api.openai.com/v1", "OPENAI_API_KEY"));

    assertEquals("OPENAI_API_KEY", config.apiKeyEnvironmentVariable());
  }

  @Test
  void shouldRejectNullProvider() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RagConfig(
                true, null, "http://localhost:11434", "model", null, 1, 1, 1, 4, 4, 1, 1, 0));
  }

  @Test
  void shouldRejectBlankModel() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RagConfig(
                true,
                EmbeddingProvider.OLLAMA,
                "http://localhost:11434",
                " ",
                null,
                1,
                1,
                1,
                4,
                4,
                1,
                1,
                0));
  }

  @Test
  void shouldRejectNonHttpBaseUrl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> valid(EmbeddingProvider.OLLAMA, "ftp://localhost", null));
  }

  @Test
  void shouldRejectMissingBaseUrl() {
    assertThrows(IllegalArgumentException.class, () -> valid(EmbeddingProvider.OLLAMA, null, null));
  }

  @Test
  void shouldRejectMalformedBaseUrl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> valid(EmbeddingProvider.OLLAMA, "http://local host", null));
  }

  @Test
  void shouldRejectBaseUrlWithQuery() {
    assertThrows(
        IllegalArgumentException.class,
        () -> valid(EmbeddingProvider.OLLAMA, "http://localhost:11434?x=1", null));
  }

  @Test
  void shouldRejectChunkSizeBelowMinimum() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RagConfig(
                true,
                EmbeddingProvider.OLLAMA,
                "http://localhost:11434",
                "model",
                null,
                1,
                1,
                1,
                3,
                3,
                1,
                1,
                0));
  }

  @Test
  void shouldRejectContextSizeSmallerThanChunkSize() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RagConfig(
                true,
                EmbeddingProvider.OLLAMA,
                "http://localhost:11434",
                "model",
                null,
                1,
                1,
                1,
                100,
                50,
                1,
                1,
                0));
  }

  @Test
  void shouldRejectMinimumScoreOutOfRange() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RagConfig(
                true,
                EmbeddingProvider.OLLAMA,
                "http://localhost:11434",
                "model",
                null,
                1,
                1,
                1,
                4,
                4,
                1,
                1,
                1.5));
  }

  private RagConfig valid(EmbeddingProvider provider, String baseUrl, String apiKeyEnv) {
    return new RagConfig(
        true, provider, baseUrl, "model", apiKeyEnv, 200000, 20, 400000, 1200, 6000, 4000, 5, 0.2);
  }
}
