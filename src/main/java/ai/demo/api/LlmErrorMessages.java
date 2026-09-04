package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.exception.LlmErrorCategory;

final class LlmErrorMessages {

  private static final String SWITCH_SUGGESTION = " Try selecting a different AI provider.";
  private static final String RETRY_SUGGESTION =
      " Try selecting a different AI provider or try again later.";

  private LlmErrorMessages() {}

  static String communicationFailure(LlmErrorCategory category, LlmProvider provider) {
    return switch (category) {
      case RATE_LIMIT ->
          "The " + provider + " AI provider is rate-limited right now." + RETRY_SUGGESTION;
      case QUOTA_EXHAUSTED ->
          "The " + provider + " AI provider has run out of quota or credits." + SWITCH_SUGGESTION;
      case AUTHENTICATION ->
          "The " + provider + " AI provider rejected the request credentials."
              + SWITCH_SUGGESTION;
      case OTHER -> "Unable to communicate with the " + provider + " AI model." + RETRY_SUGGESTION;
    };
  }

  static String communicationFailure() {
    return "Unable to communicate with the AI model." + RETRY_SUGGESTION;
  }
}
