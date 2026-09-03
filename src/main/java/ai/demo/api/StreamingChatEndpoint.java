package ai.demo.api;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.ToolCallEvent;
import ai.demo.agent.ToolResultEvent;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmException;
import ai.demo.model.app.AppOutcome;
import ai.demo.model.chat.ChatResponse;
import ai.demo.persistence.DemoQuotaStore;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StreamingChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(StreamingChatEndpoint.class);
  private static final String ERROR_EVENT = "error";

  private final ChatServiceResolver serviceResolver;
  private final ChatRequestParser requestParser;
  private final ObjectMapper objectMapper;
  private final DemoRequestGate requestGate;

  StreamingChatEndpoint(
      ChatServiceResolver serviceResolver,
      ChatRequestParser requestParser,
      ObjectMapper objectMapper,
      DemoRequestGate requestGate) {
    this.serviceResolver = serviceResolver;
    this.requestParser = requestParser;
    this.objectMapper = objectMapper;
    this.requestGate = requestGate;
  }

  void handle(Context context) {
    ResolvedChatRequest request;
    ChatService chatService;
    DemoQuotaStore.Reservation reservation;
    try {
      request = requestParser.parse(context.body());
      requestGate.validate(request.conversation());
      chatService = serviceResolver.resolve(request.provider());
      reservation = requestGate.reserve(context, true);
    } catch (ApiRequestException e) {
      writeValidationError(context, e);
      return;
    } catch (ConfigurationException e) {
      log.warn("Selected LLM provider is unavailable", e);
      writeProviderUnavailable(context);
      return;
    } catch (ai.demo.exception.DemoLimitException e) {
      writeDemoLimitError(context, e);
      return;
    }

    long started = System.nanoTime();
    AppOutcome outcome = AppOutcome.FAILED;
    try {
      prepareStream(context);
      SseEventWriter writer =
          new SseEventWriter(context.res(), context.outputStream(), objectMapper);
      outcome = stream(chatService, request, writer, reservation);
    } catch (SseConnectionException e) {
      outcome = AppOutcome.DISCONNECTED;
    } finally {
      requestGate.release(reservation);
      requestGate.recordOutcome(reservation, outcome, started);
    }
  }

  private AppOutcome stream(
      ChatService chatService,
      ResolvedChatRequest request,
      SseEventWriter writer,
      DemoQuotaStore.Reservation reservation) {
    try {
      ChatResponse response =
          chatService.ask(request.conversation(), event -> writeAgentEvent(writer, event));
      requestGate.recordUsage(reservation, response.tokenUsage().totalTokens());
      writer.send("completion", SseCompletionEvent.from(response));
      return AppOutcome.COMPLETED;
    } catch (SseConnectionException e) {
      log.debug("SSE client disconnected", e);
      return AppOutcome.DISCONNECTED;
    } catch (LlmException e) {
      log.warn("Streaming LLM request failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              "LLM_COMMUNICATION_ERROR",
              LlmErrorMessages.communicationFailure(request.provider())));
    } catch (ai.demo.exception.DemoLimitException e) {
      log.warn("Demo quota reconciliation failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              "DEMO_LIMIT_UNAVAILABLE", "The public demo is temporarily unavailable."));
    } catch (RuntimeException e) {
      log.error("Unexpected streaming chat API error", e);
      writer.send(
          ERROR_EVENT, ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred."));
    }
    return AppOutcome.FAILED;
  }

  private void writeDemoLimitError(
      Context context, ai.demo.exception.DemoLimitException exception) {
    if (exception.unavailable()) {
      ApiResponseWriter.writeError(
          context,
          503,
          "DEMO_LIMIT_UNAVAILABLE",
          "The public demo is temporarily unavailable.",
          objectMapper);
    } else {
      ApiResponseWriter.writeError(
          context,
          429,
          "DEMO_LIMIT_EXCEEDED",
          "A usage limit for this demo application has been reached. Please try again later.",
          objectMapper);
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
    } else if (event instanceof ToolCallEvent(String toolName, String ignored)) {
      writer.send("tool", SseToolEvent.running(toolName));
    } else if (event instanceof ToolResultEvent(String toolName, String ignored)) {
      writer.send("tool", SseToolEvent.completed(toolName));
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
