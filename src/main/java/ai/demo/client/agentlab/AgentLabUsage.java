package ai.demo.client.agentlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Usage counters reported by the agent lab for one request, summed over all agent steps. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentLabUsage(
    @JsonProperty("model_calls") int modelCalls,
    @JsonProperty("tool_calls") int toolCalls,
    @JsonProperty("web_searches") int webSearches,
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens) {

  public AgentLabUsage {
    if (modelCalls < 0 || toolCalls < 0 || webSearches < 0 || inputTokens < 0 || outputTokens < 0) {
      throw new IllegalArgumentException("Agent lab usage counters must not be negative");
    }
  }
}
