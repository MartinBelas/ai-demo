package ai.demo.agent;

public sealed interface AgentDecision permits ToolCallDecision, ModelReply {}
