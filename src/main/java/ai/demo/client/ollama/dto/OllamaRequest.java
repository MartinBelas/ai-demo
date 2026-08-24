package ai.demo.client.ollama.dto;

import java.util.List;
import java.util.Objects;

public record OllamaRequest(
    String model, List<OllamaMessage> messages, boolean stream, OllamaOptions options) {

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

    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
  }
}
