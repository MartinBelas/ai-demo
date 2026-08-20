package ai.demo.agent;

public record ToolCallDecision(String toolName, String input) implements AgentDecision {

  public ToolCallDecision {
    if (toolName == null || toolName.isBlank()) {
      throw new IllegalArgumentException("toolName must not be blank");
    }

    if (input == null || input.isBlank()) {
      throw new IllegalArgumentException("input must not be blank");
    }
  }
}
