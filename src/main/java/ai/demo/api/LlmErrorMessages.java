package ai.demo.api;

import ai.demo.config.LlmProvider;

final class LlmErrorMessages {

  private static final String RETRY_SUGGESTION =
      " Try selecting a different AI provider or try again later.";

  private LlmErrorMessages() {}

  static String communicationFailure(LlmProvider provider) {
    return "Unable to communicate with the " + provider + " AI model." + RETRY_SUGGESTION;
  }

  static String communicationFailure() {
    return "Unable to communicate with the AI model." + RETRY_SUGGESTION;
  }
}
