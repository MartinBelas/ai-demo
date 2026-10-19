package ai.demo.client.agentlab;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.AgentLabAuthentication;
import ai.demo.config.AgentLabConfig;
import ai.demo.exception.AgentServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for the ai-agent-lab service ({@code POST /api/chat}, contract v1, no streaming).
 *
 * <p>Separate from {@code LlmClient}: the agent lab is not an LLM provider but an agent that runs
 * its own model and tools. Each call gets a short request ID that is logged here and sent as the
 * {@value #REQUEST_ID_HEADER} header, so one request can be traced in both services. Neither the
 * message nor the reply is logged, and error responses are reported only by their HTTP status.
 * Calls are authenticated as configured ({@link AgentLabAuthentication}).
 */
public final class AgentLabClient {

  private static final Logger log = LoggerFactory.getLogger(AgentLabClient.class);

  // Fixed path of the agent lab's API contract, not a deployment setting.
  @SuppressWarnings("java:S1075")
  private static final String CHAT_PATH = "/api/chat";

  static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String JSON = "application/json";

  private final AgentLabConfig config;
  private final HttpTransport transport;
  private final ObjectMapper mapper;
  private final URI chatUri;
  private final AgentLabAuthorization authorization;

  public AgentLabClient(AgentLabConfig config, HttpTransport transport, ObjectMapper mapper) {
    this(config, transport, mapper, authorization(config, transport, mapper));
  }

  AgentLabClient(
      AgentLabConfig config,
      HttpTransport transport,
      ObjectMapper mapper,
      AgentLabAuthorization authorization) {
    requireEnabled(config);
    this.config = config;
    this.transport = transport;
    this.mapper = mapper;
    this.chatUri = URI.create(config.baseUrl() + CHAT_PATH);
    this.authorization = authorization;
  }

  private static AgentLabAuthorization authorization(
      AgentLabConfig config, HttpTransport transport, ObjectMapper mapper) {
    requireEnabled(config);
    return switch (config.authentication()) {
      case NONE -> AgentLabAuthorization.NONE;
      case GOOGLE_ID_TOKEN ->
          new GoogleIdentityTokenAuthorization(config.baseUrl(), transport, mapper);
    };
  }

  private static void requireEnabled(AgentLabConfig config) {
    if (config == null || !config.enabled()) {
      throw new IllegalArgumentException("Agent lab must be enabled to create its client");
    }
  }

  public AgentLabResponse chat(AgentLabRequest request) {
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    long start = System.nanoTime();
    log.info("[{}] Sending agent lab request (history={})", requestId, request.history().size());
    try {
      HttpResponse<String> response = transport.send(httpRequest(request, requestId));
      AgentLabResponse result = parse(response);
      AgentLabUsage usage = result.usage();
      log.info(
          "[{}] Agent lab replied in {} ms (modelCalls={}, toolCalls={}, webSearches={}, "
              + "tokens={}/{}, sources={})",
          requestId,
          elapsedMillis(start),
          usage.modelCalls(),
          usage.toolCalls(),
          usage.webSearches(),
          usage.inputTokens(),
          usage.outputTokens(),
          result.sources().size());
      return result;
    } catch (AgentServiceException e) {
      throw logged(requestId, start, e);
    } catch (HttpTimeoutException e) {
      throw logged(requestId, start, new AgentServiceException("Agent lab request timed out", e));
    } catch (IOException e) {
      throw logged(requestId, start, new AgentServiceException("Unable to reach the agent lab", e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw logged(
          requestId, start, new AgentServiceException("Agent lab request was interrupted", e));
    }
  }

  private HttpRequest httpRequest(AgentLabRequest request, String requestId)
      throws JsonProcessingException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(chatUri)
            .timeout(config.timeout())
            .header("Content-Type", JSON)
            .header("Accept", JSON)
            .header(REQUEST_ID_HEADER, requestId);
    authorization.header().ifPresent(value -> builder.header("Authorization", value));
    return builder
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
        .build();
  }

  private AgentLabResponse parse(HttpResponse<String> response) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      // The body is not used: it may contain details that must not reach users or logs.
      throw new AgentServiceException("Agent lab returned HTTP " + status);
    }
    AgentLabResponse result;
    try {
      result = mapper.readValue(response.body(), AgentLabResponse.class);
    } catch (JsonProcessingException e) {
      throw new AgentServiceException("Agent lab returned an invalid response", e);
    }
    if (result == null) {
      throw new AgentServiceException("Agent lab returned an empty response");
    }
    return result;
  }

  private static AgentServiceException logged(
      String requestId, long start, AgentServiceException exception) {
    log.warn("[{}] {} after {} ms", requestId, exception.getMessage(), elapsedMillis(start));
    return exception;
  }

  private static long elapsedMillis(long start) {
    return (System.nanoTime() - start) / 1_000_000L;
  }
}
