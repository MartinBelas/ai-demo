package ai.demo.agent;

import ai.demo.client.TokenUsage;
import java.util.Objects;

/**
 * Token usage of one finished LLM call. Emitted after every call, including calls whose answer is
 * later rejected, so callers can account for tokens even when the request fails.
 */
public record TokenUsageEvent(TokenUsage usage) implements AgentEvent {

  public TokenUsageEvent {
    Objects.requireNonNull(usage, "usage");
  }
}
