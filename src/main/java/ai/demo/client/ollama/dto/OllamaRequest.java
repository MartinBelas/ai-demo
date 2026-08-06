package ai.demo.client.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public record OllamaRequest(
    String model,
    List<OllamaMessage> messages,
    boolean stream,
    @JsonProperty("repeat_penalty") double repeatPenalty) {

  public OllamaRequest {

    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }

    if (messages == null) {
      throw new IllegalArgumentException("messages must not be null");
    }

    if (messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }

    if (messages.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("messages must not contain null elements");
    }

    if (repeatPenalty < 1.0) {
      throw new IllegalArgumentException("repeatPenalty must be at least 1.0");
    }
  }
}
