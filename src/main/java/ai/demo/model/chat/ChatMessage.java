package ai.demo.model.chat;

public record ChatMessage(Role role, String content, String toolName) {

  public ChatMessage {
    if (role == null) {
      throw new IllegalArgumentException("role must not be null");
    }

    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }

    if (role == Role.TOOL && (toolName == null || toolName.isBlank())) {
      throw new IllegalArgumentException("toolName must not be blank for tool messages");
    }

    if (role != Role.TOOL && toolName != null) {
      throw new IllegalArgumentException("toolName is only supported for tool messages");
    }
  }

  public static ChatMessage system(String content) {
    return new ChatMessage(Role.SYSTEM, content, null);
  }

  public static ChatMessage user(String content) {
    return new ChatMessage(Role.USER, content, null);
  }

  public static ChatMessage assistant(String content) {
    return new ChatMessage(Role.ASSISTANT, content, null);
  }

  public static ChatMessage tool(String toolName, String content) {
    return new ChatMessage(Role.TOOL, content, toolName);
  }
}
