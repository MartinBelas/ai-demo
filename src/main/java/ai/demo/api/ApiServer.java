package ai.demo.api;

import ai.demo.exception.ServerException;
import io.javalin.Javalin;

/** Embedded HTTP server exposing the public API. */
public final class ApiServer implements AutoCloseable {

  private static final String HEALTH_RESPONSE = "{\"status\":\"UP\"}";

  private final int configuredPort;
  private final String openApiDocument;
  private Javalin app;

  public ApiServer(int configuredPort) {
    this.configuredPort = configuredPort;
    this.openApiDocument = OpenApiDocument.load();
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
                                  context.contentType("application/json").result(HEALTH_RESPONSE))
                          .get(
                              "/openapi.yaml",
                              context ->
                                  context.contentType("application/yaml").result(openApiDocument)))
              .start(configuredPort);
    } catch (RuntimeException e) {
      throw new ServerException("Unable to start HTTP server", e);
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
