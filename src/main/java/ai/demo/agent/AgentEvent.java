package ai.demo.agent;

public sealed interface AgentEvent
    permits ThinkingEvent, ToolCallEvent, ToolResultEvent, ContentEvent {}
