package ai.demo.agent;

public record ModelReply(String content) implements AgentDecision {

  public ModelReply {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
  }
}
