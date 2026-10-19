package ai.demo.client.agentlab;

import java.util.List;

/** Request body of the agent lab's {@code POST /api/chat} (contract v1). */
public record AgentLabRequest(String message, List<AgentLabMessage> history) {

  public AgentLabRequest {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Agent lab message must not be blank");
    }
    history = history == null ? List.of() : List.copyOf(history);
  }
}
