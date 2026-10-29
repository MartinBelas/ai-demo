package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApiRequestLoggerTest {

  private static final String LOCAL_SERVER = "http://localhost:";
  private static final String HEALTH_PATH = "/api/health";
  private static final long LOG_WAIT_MILLIS = 2000;

  @Test
  void shouldLogApiRequestWithMethodPathStatusAndDuration()
      throws IOException, InterruptedException {
    List<String> lines = logLinesFor(HEALTH_PATH);

    assertEquals(1, lines.size());
    assertTrue(lines.getFirst().matches("GET /api/health -> 200 \\(\\d+ ms\\)"), lines.getFirst());
  }

  @Test
  void shouldLogUnknownApiPathWithItsStatus() throws IOException, InterruptedException {
    List<String> lines = logLinesFor("/api/does-not-exist", HEALTH_PATH);

    assertTrue(
        lines.stream().anyMatch(line -> line.startsWith("GET /api/does-not-exist -> 404 ")),
        lines.toString());
  }

  @Test
  void shouldNotLogQueryString() throws IOException, InterruptedException {
    List<String> lines = logLinesFor(HEALTH_PATH + "?token=secret-value");

    assertEquals(1, lines.size());
    assertFalse(lines.getFirst().contains("secret-value"), lines.getFirst());
    assertFalse(lines.getFirst().contains("?"), lines.getFirst());
  }

  @Test
  void shouldNotLogNonApiRequests() throws IOException, InterruptedException {
    // The health request goes last and marks the point where all earlier requests are done.
    List<String> lines = logLinesFor("/", "/openapi.yaml", HEALTH_PATH);

    assertEquals(1, lines.size(), lines.toString());
    assertTrue(lines.getFirst().startsWith("GET /api/health "), lines.getFirst());
  }

  /**
   * Sends GET requests to the given paths and returns the messages logged by ApiRequestLogger.
   * Javalin calls the request logger after the response has been written, so the method waits
   * (without polling) until the last path has been logged.
   */
  private static List<String> logLinesFor(String... paths)
      throws IOException, InterruptedException {
    String lastPath = paths[paths.length - 1].split("\\?")[0];
    Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestLogger.class);
    LatchAppender appender = new LatchAppender(" " + lastPath + " ");
    appender.start();
    logger.addAppender(appender);

    try (ApiServer server = TestApiServer.create().build();
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();
      for (String path : paths) {
        client.send(
            HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + path)).GET().build(),
            HttpResponse.BodyHandlers.discarding());
      }
      assertTrue(appender.awaitLogged(), "Request to " + lastPath + " was not logged");
    } finally {
      logger.detachAppender(appender);
    }
    return appender.messages();
  }

  /** Collects log messages and signals once a message containing the given fragment arrives. */
  private static final class LatchAppender extends AppenderBase<ILoggingEvent> {

    private final String fragment;
    private final CountDownLatch logged = new CountDownLatch(1);
    private final List<String> messages = new CopyOnWriteArrayList<>();

    LatchAppender(String fragment) {
      this.fragment = fragment;
    }

    @Override
    protected void append(ILoggingEvent event) {
      String message = event.getFormattedMessage();
      messages.add(message);
      if (message.contains(fragment)) {
        logged.countDown();
      }
    }

    boolean awaitLogged() throws InterruptedException {
      return logged.await(LOG_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }

    List<String> messages() {
      return List.copyOf(messages);
    }
  }
}
