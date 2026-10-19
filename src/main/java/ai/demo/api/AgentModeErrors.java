package ai.demo.api;

/** Public, provider-independent error codes and messages of agent mode. */
final class AgentModeErrors {

  static final String DISABLED_CODE = "AGENT_MODE_DISABLED";
  static final String DISABLED_MESSAGE = "Agent mode is not enabled in this deployment.";
  static final String UNAVAILABLE_CODE = "AGENT_UNAVAILABLE";
  static final String UNAVAILABLE_MESSAGE =
      "Agent mode is temporarily unavailable. Try again later or turn agent mode off.";

  private AgentModeErrors() {}
}
