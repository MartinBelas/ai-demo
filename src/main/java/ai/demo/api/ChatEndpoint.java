package ai.demo.api;

import ai.demo.exception.ApiRequestException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(ChatEndpoint.class);
  private final ChatServiceResolver serviceResolver;
  private final ChatRequestParser requestParser;
  private final ObjectMapper objectMapper;

  ChatEndpoint(
      ChatServiceResolver serviceResolver,
      ChatRequestParser requestParser,
      ObjectMapper objectMapper) {
    this.serviceResolver = serviceResolver;
    this.requestParser = requestParser;
    this.objectMapper = objectMapper;
  }

  void handle(Context context) {
    try {
      ResolvedChatRequest request = requestParser.parse(context.body());
      ChatResponse response =
          serviceResolver.resolve(request.provider()).ask(request.conversation());
      ApiResponseWriter.write(context, 200, ApiChatResponse.from(response), objectMapper);
    } catch (ApiRequestException e) {
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
    ApiResponseWriter.writeError(context, status, code, message, objectMapper);
  }
}
