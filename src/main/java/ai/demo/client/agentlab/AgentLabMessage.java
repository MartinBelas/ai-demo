package ai.demo.client.agentlab;

/** An earlier conversation turn sent to the agent lab as history (user or assistant only). */
public record AgentLabMessage(String role, String content) {

  private static final String USER = "user";
  private static final String ASSISTANT = "assistant";

  public AgentLabMessage {
    if (!USER.equals(role) && !ASSISTANT.equals(role)) {
      throw new IllegalArgumentException(
          "Agent lab history supports only user and assistant roles");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Agent lab history message must not be blank");
    }
  }

  public static AgentLabMessage user(String content) {
    return new AgentLabMessage(USER, content);
  }

  public static AgentLabMessage assistant(String content) {
    return new AgentLabMessage(ASSISTANT, content);
  }
}
