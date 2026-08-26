package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmException;
import ai.demo.exception.ServerException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(ChatEndpoint.class);
  private static final String JSON = "application/json";

  private final ChatServiceResolver serviceResolver;
  private final LlmProvider defaultProvider;
  private final ObjectMapper objectMapper;

  ChatEndpoint(
      ChatServiceResolver serviceResolver, LlmProvider defaultProvider, ObjectMapper objectMapper) {
    this.serviceResolver = serviceResolver;
    this.defaultProvider = defaultProvider;
    this.objectMapper = objectMapper;
  }

  void handle(Context context) {
    try {
      ChatRequest request = objectMapper.readValue(context.body(), ChatRequest.class);
      if (request == null) {
        throw new ApiRequestException("Chat request is required");
      }
      Conversation conversation = request.toConversation();
      LlmProvider provider = request.selectedProvider(defaultProvider);
      ChatResponse response = serviceResolver.resolve(provider).ask(conversation);
      write(context, 200, ApiChatResponse.from(response));
    } catch (JsonProcessingException | ApiRequestException e) {
      writeError(context, 400, "INVALID_REQUEST", "Invalid chat request.");
    } catch (ConfigurationException e) {
      writeError(
          context, 400, "LLM_PROVIDER_UNAVAILABLE", "The selected LLM provider is not available.");
    } catch (LlmException e) {
      log.warn("LLM request failed: {}", e.getMessage());
      writeError(
          context, 502, "LLM_COMMUNICATION_ERROR", "Unable to communicate with the AI model.");
    } catch (RuntimeException e) {
      log.error("Unexpected chat API error", e);
      writeError(context, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }
  }

  private void writeError(Context context, int status, String code, String message) {
    write(context, status, ApiErrorResponse.of(code, message));
  }

  private void write(Context context, int status, Object response) {
    try {
      context.status(status).contentType(JSON).result(objectMapper.writeValueAsString(response));
    } catch (JsonProcessingException e) {
      throw new ServerException("Unable to serialize API response", e);
    }
  }
}
