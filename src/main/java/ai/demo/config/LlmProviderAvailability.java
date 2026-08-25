package ai.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/** Resolves providers that have both configuration and required credentials. */
public final class LlmProviderAvailability {

  private final AppConfig config;
  private final UnaryOperator<String> environment;

  public LlmProviderAvailability(AppConfig config, UnaryOperator<String> environment) {
    this.config = config;
    this.environment = environment;
  }

  public List<AvailableLlmProvider> availableProviders() {
    return Arrays.stream(LlmProvider.values())
        .filter(this::isAvailable)
        .map(provider -> new AvailableLlmProvider(provider, config.model(provider)))
        .toList();
  }

  private boolean isAvailable(LlmProvider provider) {
    return switch (provider) {
      case OLLAMA -> config.ollama() != null && config.ollama().enabled();
      case OPENAI ->
          config.openAi() != null && hasValue(config.openAi().apiKeyEnvironmentVariable());
      case GROQ -> config.groq() != null && hasValue(config.groq().apiKeyEnvironmentVariable());
      case GEMINI ->
          config.gemini() != null && hasValue(config.gemini().apiKeyEnvironmentVariable());
    };
  }

  private boolean hasValue(String variable) {
    String value = environment.apply(variable);
    return value != null && !value.isBlank();
  }
}
