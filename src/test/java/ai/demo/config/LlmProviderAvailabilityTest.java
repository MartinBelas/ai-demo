package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LlmProviderAvailabilityTest {

  @Test
  void shouldReturnOnlyEnabledAndCredentialedProviders() {
    AppConfig config =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(new GenerationConfig(0.4, 1000, "Be helpful."))
            .ollama(new OllamaConfig("ollama-model", "http://localhost:11434", 4096, 1.18, true))
            .openAi(
                new OpenAiConfig(
                    "openai-model", "https://api.openai.com/v1", "OPENAI_API_KEY", true))
            .groq(new GroqConfig("groq-model", "https://api.groq.com/openai/v1", "GROQ_API_KEY"))
            .gemini(
                new GeminiConfig(
                    "gemini-model",
                    "https://generativelanguage.googleapis.com/v1beta",
                    "GEMINI_API_KEY"))
            .build();
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
