package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmProviderAvailabilityTest {

  @Test
  void shouldReturnOnlyEnabledAndCredentialedProviders() {
    AppConfig config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.4, 1000, "Be helpful."),
            new OllamaConfig("ollama-model", "http://localhost:11434", 4096, 1.18, true),
            new OpenAiConfig("openai-model", "https://api.openai.com/v1", "OPENAI_API_KEY"),
            new GroqConfig("groq-model", "https://api.groq.com/openai/v1", "GROQ_API_KEY"),
            new GeminiConfig(
                "gemini-model",
                "https://generativelanguage.googleapis.com/v1beta",
                "GEMINI_API_KEY"),
            Path.of("conversation.json"));
    LlmProviderAvailability availability =
        new LlmProviderAvailability(config, key -> "GEMINI_API_KEY".equals(key) ? null : "secret");

    List<AvailableLlmProvider> providers = availability.availableProviders();

    assertEquals(
        List.of(
            new AvailableLlmProvider(LlmProvider.OLLAMA, "ollama-model"),
            new AvailableLlmProvider(LlmProvider.OPENAI, "openai-model"),
            new AvailableLlmProvider(LlmProvider.GROQ, "groq-model")),
        providers);
  }
}
