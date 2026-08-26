package ai.demo.api;

import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.ChatMessage;
import java.util.Locale;

record ApiChatMessage(String role, String content) {

  ChatMessage toDomain(String fieldPrefix) {
    if (role == null || role.isBlank()) {
      throw new ApiRequestException(fieldPrefix + ".role", "Role is required.");
    }
    if (content == null || content.isBlank()) {
      throw new ApiRequestException(fieldPrefix + ".content", "Content must not be blank.");
    }
    return switch (role.trim().toUpperCase(Locale.ROOT)) {
      case "USER" -> ChatMessage.user(content);
      case "ASSISTANT" -> ChatMessage.assistant(content);
      default -> throw new ApiRequestException(fieldPrefix + ".role", "Role is invalid.");
    };
  }
}
