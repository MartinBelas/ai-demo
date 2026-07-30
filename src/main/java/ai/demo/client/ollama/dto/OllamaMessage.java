package ai.demo.client.ollama.dto;

public record OllamaMessage(String role, String content) {

  public OllamaMessage {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role must not be blank");
    }

    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
  }
}
