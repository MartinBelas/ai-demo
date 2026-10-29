package ai.demo.api;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.TokenUsageEvent;
import java.util.function.Consumer;

/**
 * Sums the token usage the agent reports for one API request, so the demo quota can be charged for
 * every LLM call that finished, also when the request fails afterwards. A request is handled on one
 * thread, so no synchronization is needed.
 */
final class TokenMeter {

  private int totalTokens;
  private boolean charged;

  /** Records usage events and passes every other event on to {@code downstream}. */
  Consumer<AgentEvent> observe(Consumer<AgentEvent> downstream) {
    return event -> {
      if (event instanceof TokenUsageEvent(var usage)) {
        totalTokens += usage.totalTokens();
      } else {
        downstream.accept(event);
      }
    };
  }

  int totalTokens() {
    return totalTokens;
  }

  /** Marks the usage as charged, before charging, so a failed charge is not repeated. */
  void markCharged() {
    charged = true;
  }

  boolean charged() {
    return charged;
  }
}
