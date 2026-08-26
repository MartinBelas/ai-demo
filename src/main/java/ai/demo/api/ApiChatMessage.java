package ai.demo.api;

import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.ChatMessage;
import java.util.Locale;

record ApiChatMessage(String role, String content) {

  ChatMessage toDomain() {
    if (role == null || content == null || content.isBlank()) {
      throw new ApiRequestException("Chat message role and content are required");
    }
    return switch (role.trim().toUpperCase(Locale.ROOT)) {
      case "USER" -> ChatMessage.user(content);
      case "ASSISTANT" -> ChatMessage.assistant(content);
      default -> throw new ApiRequestException("Unsupported chat message role");
    };
  }
}
