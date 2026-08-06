package ai.demo.client.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaMessage(String role, String content, String thinking) {

  public OllamaMessage {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role must not be blank");
    }

    // Content may be empty for streaming chunks (partial updates). It must not be null,
    // but blank content is allowed and handled by the streaming logic. The provider may
    // also send partial text in a separate `thinking` field; the client will prefer
    // `content` and fall back to `thinking` when content is blank.
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    // `thinking` may be null/blank - that's acceptable for response parsing.
  }
}
