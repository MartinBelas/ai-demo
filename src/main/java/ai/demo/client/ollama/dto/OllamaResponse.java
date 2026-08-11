package ai.demo.client.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaResponse(
    String model,
    OllamaMessage message,
    boolean done,
    @JsonProperty("prompt_eval_count") Integer promptEvalCount,
    @JsonProperty("eval_count") Integer evalCount) {

  public OllamaResponse {
    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }
    if (model == null || model.trim().isEmpty()) {
      throw new IllegalArgumentException("Model cannot be null or empty");
    }
  }
}
