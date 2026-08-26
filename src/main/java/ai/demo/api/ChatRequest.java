package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.Conversation;
import java.util.List;

record ChatRequest(String provider, List<ApiChatMessage> messages) {

  LlmProvider selectedProvider(LlmProvider defaultProvider) {
    if (provider == null || provider.isBlank()) {
      return defaultProvider;
    }
    try {
      return LlmProvider.from(provider);
    } catch (IllegalArgumentException e) {
      throw new ApiRequestException("Unsupported LLM provider", e);
    }
  }

  Conversation toConversation() {
    if (messages == null || messages.isEmpty()) {
      throw new ApiRequestException("At least one chat message is required");
    }
    Conversation conversation = new Conversation();
    messages.stream()
        .map(
            message -> {
              if (message == null) {
                throw new ApiRequestException("Chat messages must not be null");
              }
              return message.toDomain();
            })
        .forEach(conversation::add);
    if (conversation.messages().getLast().role() != ai.demo.model.chat.Role.USER) {
      throw new ApiRequestException("The last chat message must have the USER role");
    }
    return conversation;
  }
}
