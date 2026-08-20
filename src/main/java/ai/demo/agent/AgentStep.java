package ai.demo.agent;

import ai.demo.client.LlmResponse;

record AgentStep(AgentDecision decision, LlmResponse response) {}
