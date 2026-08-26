package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.exception.ApiRequestException;
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
      return new ResolvedChatRequest(
          request.selectedProvider(defaultProvider), request.toConversation());
    } catch (JsonProcessingException e) {
      throw new ApiRequestException("request", "Request body must contain valid JSON.", e);
    }
  }
}
