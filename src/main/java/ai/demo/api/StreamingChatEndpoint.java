package ai.demo.api;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.ToolCallEvent;
import ai.demo.agent.ToolResultEvent;
import ai.demo.exception.AgentDecisionException;
import ai.demo.exception.AgentModeDisabledException;
import ai.demo.exception.AgentServiceException;
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
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StreamingChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(StreamingChatEndpoint.class);
  private static final String ERROR_EVENT = "error";
  private static final String DEMO_LIMIT_UNAVAILABLE_CODE = "DEMO_LIMIT_UNAVAILABLE";
  private static final String DEMO_LIMIT_UNAVAILABLE_MESSAGE =
      "The public demo is temporarily unavailable.";

  private final ChatServiceSelector serviceSelector;
  private final ChatRequestParser requestParser;
  private final ObjectMapper objectMapper;
  private final DemoRequestGate requestGate;

  StreamingChatEndpoint(
      ChatServiceSelector serviceSelector,
      ChatRequestParser requestParser,
      ObjectMapper objectMapper,
      DemoRequestGate requestGate) {
    this.serviceSelector = serviceSelector;
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
      chatService = serviceSelector.select(request);
      if (request.rag()) chatService.validateRag(request.conversation());
      reservation = requestGate.reserve(context, true);
    } catch (AgentModeDisabledException e) {
      ApiResponseWriter.writeError(
          context,
          400,
          AgentModeErrors.DISABLED_CODE,
          AgentModeErrors.DISABLED_MESSAGE,
          objectMapper);
      return;
    } catch (ai.demo.exception.RagDisabledException e) {
      ApiResponseWriter.writeError(
          context,
          400,
          "RAG_DISABLED",
          "Retrieval-augmented generation is not enabled in this deployment.",
          objectMapper);
      return;
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
    TokenMeter tokenMeter = new TokenMeter();
    try {
      prepareStream(context);
      SseEventWriter writer =
          new SseEventWriter(context.res(), context.outputStream(), objectMapper);
      outcome = stream(chatService, request, writer, reservation, tokenMeter);
    } catch (SseConnectionException e) {
      outcome = AppOutcome.DISCONNECTED;
    } finally {
      // A failed or disconnected stream is still charged for every LLM call that finished.
      if (!tokenMeter.charged()) {
        requestGate.recordUsageAfterFailure(reservation, tokenMeter.totalTokens());
      }
      requestGate.release(reservation);
      requestGate.recordOutcome(reservation, outcome, started);
    }
  }

  private AppOutcome stream(
      ChatService chatService,
      ResolvedChatRequest request,
      SseEventWriter writer,
      DemoQuotaStore.Reservation reservation,
      TokenMeter tokenMeter) {
    Consumer<AgentEvent> events = tokenMeter.observe(event -> writeAgentEvent(writer, event));
    try {
      ChatResponse response =
          request.rag()
              ? chatService.askWithRag(request.conversation(), events)
              : chatService.ask(request.conversation(), events);
      tokenMeter.markCharged();
      requestGate.recordUsage(reservation, response.tokenUsage().totalTokens());
      writer.send("completion", SseCompletionEvent.from(response));
      return AppOutcome.COMPLETED;
    } catch (SseConnectionException e) {
      log.debug("SSE client disconnected", e);
      return AppOutcome.DISCONNECTED;
    } catch (AgentDecisionException e) {
      // Raised by the local tool-calling agent in normal chat, not by agent mode.
      log.warn("Streaming LLM returned an invalid agent decision", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              LlmErrorMessages.INVALID_RESPONSE_CODE, LlmErrorMessages.invalidResponse()));
    } catch (AgentServiceException e) {
      log.warn("Streaming agent mode request failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              AgentModeErrors.UNAVAILABLE_CODE, AgentModeErrors.UNAVAILABLE_MESSAGE));
    } catch (ai.demo.exception.RagException e) {
      log.warn("Streaming RAG request failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              "RAG_UNAVAILABLE", "Project document search is temporarily unavailable."));
    } catch (LlmException e) {
      log.warn("Streaming LLM request failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(
              "LLM_COMMUNICATION_ERROR",
              LlmErrorMessages.communicationFailure(e.category(), request.provider())));
    } catch (ai.demo.exception.DemoLimitException e) {
      log.warn("Demo quota reconciliation failed", e);
      writer.send(
          ERROR_EVENT,
          ApiErrorResponse.of(DEMO_LIMIT_UNAVAILABLE_CODE, DEMO_LIMIT_UNAVAILABLE_MESSAGE));
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
          context, 503, DEMO_LIMIT_UNAVAILABLE_CODE, DEMO_LIMIT_UNAVAILABLE_MESSAGE, objectMapper);
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
