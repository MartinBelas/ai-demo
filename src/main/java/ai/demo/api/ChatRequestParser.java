package ai.demo.api;

import ai.demo.agent.RemoteAgent;
import ai.demo.config.LlmProvider;
import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ChatRequestParser {

  private final LlmProvider defaultProvider;
  private final ObjectMapper objectMapper;

  ChatRequestParser(LlmProvider defaultProvider, ObjectMapper objectMapper) {
    this.defaultProvider = defaultProvider;
    this.objectMapper = objectMapper;
  }

  ResolvedChatRequest parse(String body) {
    try {
      ChatRequest request = objectMapper.readValue(body, ChatRequest.class);
      if (request == null) {
        throw new ApiRequestException("request", "Request body is required.");
      }
      Conversation conversation = request.toConversation();
      if (request.agent()) {
        validateAgentRequest(request, conversation);
      }
      return new ResolvedChatRequest(
          request.selectedProvider(defaultProvider), conversation, request.rag(), request.agent());
    } catch (JsonProcessingException e) {
      throw new ApiRequestException("request", "Request body must contain valid JSON.", e);
    }
  }

  private static void validateAgentRequest(ChatRequest request, Conversation conversation) {
    if (request.rag()) {
      throw new ApiRequestException("rag", "Agent mode cannot be combined with RAG.");
    }
    ChatMessage latest = conversation.messages().getLast();
    if (latest.content().length() > RemoteAgent.MAX_MESSAGE_CHARACTERS) {
      throw new ApiRequestException(
          "messages[" + (conversation.size() - 1) + "].content",
          "Agent mode accepts at most "
              + RemoteAgent.MAX_MESSAGE_CHARACTERS
              + " characters per message.");
    }
  }
}
