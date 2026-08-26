package ai.demo.api;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StreamingChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(StreamingChatEndpoint.class);

  private final ChatServiceResolver serviceResolver;
  private final ChatRequestParser requestParser;
  private final ObjectMapper objectMapper;

  StreamingChatEndpoint(
      ChatServiceResolver serviceResolver,
      ChatRequestParser requestParser,
      ObjectMapper objectMapper) {
    this.serviceResolver = serviceResolver;
    this.requestParser = requestParser;
    this.objectMapper = objectMapper;
  }

  void handle(Context context) {
    ResolvedChatRequest request;
    ChatService chatService;
    try {
      request = requestParser.parse(context.body());
      chatService = serviceResolver.resolve(request.provider());
    } catch (ApiRequestException e) {
      writeValidationError(context, e);
      return;
    } catch (ConfigurationException e) {
      log.warn("Selected LLM provider is unavailable", e);
      writeProviderUnavailable(context);
      return;
    }

    prepareStream(context);
    SseEventWriter writer = new SseEventWriter(context.outputStream(), objectMapper);
    stream(chatService, request, writer);
  }

  private void stream(ChatService chatService, ResolvedChatRequest request, SseEventWriter writer) {
    try {
      ChatResponse response =
          chatService.ask(request.conversation(), event -> writeAgentEvent(writer, event));
      writer.send("completion", SseCompletionEvent.from(response));
    } catch (SseConnectionException e) {
      log.debug("SSE client disconnected", e);
    } catch (LlmException e) {
      log.warn("Streaming LLM request failed", e);
      writer.send(
          "error",
          ApiErrorResponse.of(
              "LLM_COMMUNICATION_ERROR", "Unable to communicate with the AI model."));
    } catch (RuntimeException e) {
      log.error("Unexpected streaming chat API error", e);
      writer.send("error", ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred."));
    }
  }

  private void prepareStream(Context context) {
    context
        .status(200)
        .contentType("text/event-stream")
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .disableCompression();
    context.res().setCharacterEncoding(StandardCharsets.UTF_8.name());
  }

  private void writeAgentEvent(SseEventWriter writer, AgentEvent event) {
    if (event instanceof ThinkingEvent(String content)) {
      writer.send("thinking", new SseTextEvent(content));
    } else if (event instanceof ContentEvent(String content)) {
      writer.send("content", new SseTextEvent(content));
    }
  }

  private void writeValidationError(Context context, ApiRequestException exception) {
    ApiErrorResponse error =
        ApiErrorResponse.withDetail(
            "INVALID_REQUEST", "Invalid chat request.", exception.field(), exception.getMessage());
    ApiResponseWriter.write(context, 400, error, objectMapper);
  }

  private void writeProviderUnavailable(Context context) {
    ApiResponseWriter.writeError(
        context,
        400,
        "LLM_PROVIDER_UNAVAILABLE",
        "The selected LLM provider is not available.",
        objectMapper);
  }
}
