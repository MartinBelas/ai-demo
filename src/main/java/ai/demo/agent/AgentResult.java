package ai.demo.agent;

import ai.demo.client.TokenUsage;

public record AgentResult(String answer, String model, TokenUsage tokenUsage) {}
