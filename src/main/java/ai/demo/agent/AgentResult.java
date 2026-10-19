package ai.demo.agent;

import ai.demo.client.TokenUsage;
import ai.demo.model.chat.WebSource;
import java.util.List;

/**
 * Result of one agent run. {@code webSources} are web pages an external agent found; the built-in
 * agent never has any.
 */
public record AgentResult(
    String answer, String model, TokenUsage tokenUsage, List<WebSource> webSources) {

  public AgentResult(String answer, String model, TokenUsage tokenUsage) {
    this(answer, model, tokenUsage, List.of());
  }

  public AgentResult {
    webSources = webSources == null ? List.of() : List.copyOf(webSources);
  }
}
