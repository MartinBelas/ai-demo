package ai.demo.agent;

public record ToolCallEvent(String toolName, String input) implements AgentEvent {}
