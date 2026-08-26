package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.exception.ServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

/** Embedded HTTP server exposing the public API. */
public final class ApiServer implements AutoCloseable {

  private static final String HEALTH_RESPONSE = "{\"status\":\"UP\"}";
  private static final String JSON_CONTENT_TYPE = "application/json";

  private final int configuredPort;
  private final String openApiDocument;
  private final LlmProviderAvailability providerAvailability;
  private final ObjectMapper objectMapper;
  private final ChatEndpoint chatEndpoint;
  private Javalin app;

  public ApiServer(int configuredPort) {
    this(configuredPort, null, null, null, new ObjectMapper());
  }

  public ApiServer(
      int configuredPort, LlmProviderAvailability providerAvailability, ObjectMapper objectMapper) {
    this(configuredPort, providerAvailability, null, null, objectMapper);
  }

  public ApiServer(
      int configuredPort,
      LlmProviderAvailability providerAvailability,
      ChatServiceResolver chatServiceResolver,
      LlmProvider defaultProvider,
      ObjectMapper objectMapper) {
    this.configuredPort = configuredPort;
    this.openApiDocument = OpenApiDocument.load();
    this.providerAvailability = providerAvailability;
    this.objectMapper = objectMapper;
    this.chatEndpoint =
        chatServiceResolver == null
            ? null
            : new ChatEndpoint(chatServiceResolver, defaultProvider, objectMapper);
  }

  public void start() {
    try {
      app =
          Javalin.create(
                  config ->
                      config
                          .routes
                          .get(
                              "/api/health",
                              context ->
                                  context.contentType(JSON_CONTENT_TYPE).result(HEALTH_RESPONSE))
                          .get(
                              "/openapi.yaml",
                              context ->
                                  context.contentType("application/yaml").result(openApiDocument))
                          .get(
                              "/api/llm/providers",
                              context ->
                                  context
                                      .contentType(JSON_CONTENT_TYPE)
                                      .result(llmProvidersResponse()))
                          .post("/api/chat", this::handleChat))
              .start(configuredPort);
    } catch (RuntimeException e) {
      throw new ServerException("Unable to start HTTP server", e);
    }
  }

  private void handleChat(io.javalin.http.Context context) {
    if (chatEndpoint == null) {
      context
          .status(503)
          .contentType(JSON_CONTENT_TYPE)
          .result("{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Chat is not available.\"}");
      return;
    }
    chatEndpoint.handle(context);
  }

  private String llmProvidersResponse() {
    if (providerAvailability == null) {
      return "{\"providers\":[]}";
    }
    try {
      return objectMapper.writeValueAsString(
          LlmProvidersResponse.from(providerAvailability.availableProviders()));
    } catch (JsonProcessingException e) {
      throw new ServerException("Unable to serialize provider response", e);
    }
  }

  public int port() {
    if (app == null) {
      throw new ServerException("HTTP server has not been started", null);
    }
    return app.port();
  }

  public void awaitShutdown() {
    if (app == null) {
      throw new ServerException("HTTP server has not been started", null);
    }
    try {
      app.jettyServer().server().join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServerException("Interrupted while waiting for HTTP server shutdown", e);
    }
  }

  @Override
  public void close() {
    if (app != null) {
      app.stop();
    }
  }
}
