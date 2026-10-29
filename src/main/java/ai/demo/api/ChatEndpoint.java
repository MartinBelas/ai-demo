package ai.demo.api;

import ai.demo.exception.AgentDecisionException;
import ai.demo.exception.AgentModeDisabledException;
import ai.demo.exception.AgentServiceException;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.DemoLimitException;
import ai.demo.exception.LlmException;
import ai.demo.exception.RagDisabledException;
import ai.demo.exception.RagException;
import ai.demo.model.app.AppOutcome;
import ai.demo.model.chat.ChatResponse;
import ai.demo.persistence.DemoQuotaStore;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ChatEndpoint {

  private static final Logger log = LoggerFactory.getLogger(ChatEndpoint.class);
  private final ChatServiceSelector serviceSelector;
  private final ChatRequestParser requestParser;
  private final ObjectMapper objectMapper;
  private final DemoRequestGate requestGate;

  ChatEndpoint(
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
    DemoQuotaStore.Reservation reservation = null;
    AppOutcome outcome = AppOutcome.FAILED;
    long started = System.nanoTime();
    ResolvedChatRequest request = null;
    TokenMeter tokenMeter = new TokenMeter();
    try {
      request = requestParser.parse(context.body());
      requestGate.validate(request.conversation());
      ChatService chatService = serviceSelector.select(request);
      if (request.rag()) chatService.validateRag(request.conversation());
      reservation = requestGate.reserve(context, false);
      ChatResponse response =
          request.rag()
              ? chatService.askWithRag(request.conversation(), tokenMeter.observe(event -> {}))
              : chatService.ask(request.conversation(), tokenMeter.observe(event -> {}));
      tokenMeter.markCharged();
      requestGate.recordUsage(reservation, response.tokenUsage().totalTokens());
      ApiResponseWriter.write(context, 200, ApiChatResponse.from(response), objectMapper);
      outcome = AppOutcome.COMPLETED;
    } catch (RagDisabledException e) {
      writeError(
          context,
          400,
          "RAG_DISABLED",
          "Retrieval-augmented generation is not enabled in this deployment.");
    } catch (AgentModeDisabledException e) {
      writeError(context, 400, AgentModeErrors.DISABLED_CODE, AgentModeErrors.DISABLED_MESSAGE);
    } catch (AgentDecisionException e) {
      // Raised by the local tool-calling agent in normal chat, not by agent mode.
      log.warn("LLM returned an invalid agent decision", e);
      writeError(
          context, 502, LlmErrorMessages.INVALID_RESPONSE_CODE, LlmErrorMessages.invalidResponse());
    } catch (AgentServiceException e) {
      log.warn("Agent mode request failed", e);
      writeError(
          context, 502, AgentModeErrors.UNAVAILABLE_CODE, AgentModeErrors.UNAVAILABLE_MESSAGE);
    } catch (RagException e) {
      log.warn("RAG request failed", e);
      writeError(
          context, 503, "RAG_UNAVAILABLE", "Project document search is temporarily unavailable.");
    } catch (ApiRequestException e) {
      writeValidationError(context, e);
    } catch (ConfigurationException e) {
      log.warn("Selected LLM provider is unavailable", e);
      writeProviderUnavailable(context);
    } catch (LlmException e) {
      log.warn("LLM request failed", e);
      String message =
          request == null
              ? LlmErrorMessages.communicationFailure()
              : LlmErrorMessages.communicationFailure(e.category(), request.provider());
      writeError(context, 502, "LLM_COMMUNICATION_ERROR", message);
    } catch (DemoLimitException e) {
      writeDemoLimitError(context, e);
    } catch (RuntimeException e) {
      log.error("Unexpected chat API error", e);
      writeError(context, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
    } finally {
      // A failed request is still charged for every LLM call that finished before the failure.
      if (!tokenMeter.charged()) {
        requestGate.recordUsageAfterFailure(reservation, tokenMeter.totalTokens());
      }
      requestGate.release(reservation);
      requestGate.recordOutcome(reservation, outcome, started);
    }
  }

  private void writeDemoLimitError(Context context, DemoLimitException exception) {
    if (exception.unavailable()) {
      writeError(
          context, 503, "DEMO_LIMIT_UNAVAILABLE", "The public demo is temporarily unavailable.");
    } else {
      writeError(
          context,
          429,
          "DEMO_LIMIT_EXCEEDED",
          "A usage limit for this demo application has been reached. Please try again later.");
    }
  }

  private void writeError(Context context, int status, String code, String message) {
    ApiResponseWriter.writeError(context, status, code, message, objectMapper);
  }

  private void writeValidationError(Context context, ApiRequestException exception) {
    ApiErrorResponse error =
        ApiErrorResponse.withDetail(
            "INVALID_REQUEST", "Invalid chat request.", exception.field(), exception.getMessage());
    ApiResponseWriter.write(context, 400, error, objectMapper);
  }

  private void writeProviderUnavailable(Context context) {
    writeError(
        context, 400, "LLM_PROVIDER_UNAVAILABLE", "The selected LLM provider is not available.");
  }
}
