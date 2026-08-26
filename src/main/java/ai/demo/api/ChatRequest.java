package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import java.util.List;

record ChatRequest(String provider, List<ApiChatMessage> messages) {

  LlmProvider selectedProvider(LlmProvider defaultProvider) {
    if (provider == null || provider.isBlank()) {
      return defaultProvider;
    }
    try {
      return LlmProvider.from(provider);
    } catch (IllegalArgumentException e) {
      throw new ApiRequestException("provider", "Unsupported LLM provider.", e);
    }
  }

  Conversation toConversation() {
    if (messages == null || messages.isEmpty()) {
      throw new ApiRequestException("messages", "At least one chat message is required.");
    }
    Conversation conversation = new Conversation();
    for (int index = 0; index < messages.size(); index++) {
      ApiChatMessage message = messages.get(index);
      String fieldPrefix = "messages[" + index + "]";
      if (message == null) {
        throw new ApiRequestException(fieldPrefix, "Chat message must not be null.");
      }
      conversation.add(message.toDomain(fieldPrefix));
    }
    if (conversation.messages().getLast().role() != Role.USER) {
      String field = "messages[" + (messages.size() - 1) + "].role";
      throw new ApiRequestException(field, "The last chat message must have the USER role.");
    }
    return conversation;
  }
}
