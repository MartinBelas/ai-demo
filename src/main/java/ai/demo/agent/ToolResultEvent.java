package ai.demo.agent;

public record ToolResultEvent(String toolName, String content) implements AgentEvent {}
