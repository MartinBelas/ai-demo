package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.logging.RequestId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RequestIdHandlerTest {

  private static final String LOCAL_SERVER = "http://localhost:";
  private static final String HEALTH_PATH = "/api/health";
  private static final String GENERATED_ID = "[0-9a-f]{8}";

  private final Logger requestLogger = (Logger) LoggerFactory.getLogger(ApiRequestLogger.class);
  private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(RequestIdHandler.class);
  private final RecordingAppender appender = new RecordingAppender();
  private ApiServer server;
  private HttpClient client;

  @BeforeEach
  void start() {
    appender.start();
    requestLogger.addAppender(appender);
    handlerLogger.addAppender(appender);
    server = TestApiServer.create().build();
    server.start();
    client = HttpClient.newHttpClient();
  }

  @AfterEach
  void stop() {
    client.close();
    server.close();
    requestLogger.detachAppender(appender);
    handlerLogger.detachAppender(appender);
  }

  @Test
  void shouldReturnCallerIdAndLogTheRequestLineWithIt() throws Exception {
    HttpResponse<Void> response = get(HEALTH_PATH, "bruno-a1b2c3d4");

    assertEquals("bruno-a1b2c3d4", responseId(response));
    ILoggingEvent requestLine = appender.awaitRequestLine();
    assertEquals("bruno-a1b2c3d4", requestLine.getMDCPropertyMap().get(RequestId.MDC_KEY));
  }

  @Test
  void shouldGenerateIdWhenMissing() throws Exception {
    HttpResponse<Void> response = get(HEALTH_PATH, null);

    String requestId = responseId(response);
    assertTrue(requestId.matches(GENERATED_ID), requestId);
    ILoggingEvent requestLine = appender.awaitRequestLine();
    assertEquals(requestId, requestLine.getMDCPropertyMap().get(RequestId.MDC_KEY));
  }

  @Test
  void shouldGiveEveryRequestItsOwnId() throws Exception {
    assertNotEquals(responseId(get(HEALTH_PATH, null)), responseId(get(HEALTH_PATH, null)));
  }

  @Test
  void shouldReplaceInvalidIdWithoutLoggingIt() throws Exception {
    HttpResponse<Void> response = get(HEALTH_PATH, "not valid;<script>");

    String requestId = responseId(response);
    assertTrue(requestId.matches(GENERATED_ID), requestId);
    appender.awaitRequestLine();
    assertTrue(
        appender.messages().stream().anyMatch(m -> m.startsWith("Ignoring invalid X-Request-ID")),
        appender.messages().toString());
    assertFalse(appender.messages().toString().contains("<script>"));
  }

  @Test
  void shouldAssignIdToUnknownApiPathsToo() throws Exception {
    HttpResponse<Void> response = get("/api/does-not-exist", "a1b2c3d4");

    assertEquals(404, response.statusCode());
    assertEquals("a1b2c3d4", responseId(response));
  }

  @Test
  void shouldNotAssignIdOutsideTheApi() throws Exception {
    HttpResponse<Void> response = get("/openapi.yaml", "a1b2c3d4");

    assertTrue(response.headers().firstValue(RequestId.HEADER).isEmpty());
  }

  private HttpResponse<Void> get(String path, String requestId)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + path)).GET();
    if (requestId != null) {
      request.header(RequestId.HEADER, requestId);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
  }

  private static String responseId(HttpResponse<?> response) {
    return response.headers().firstValue(RequestId.HEADER).orElseThrow();
  }

  /** Records log events and lets a test wait for the ApiRequestLogger line, which comes last. */
  private static final class RecordingAppender extends AppenderBase<ILoggingEvent> {

    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch requestLogged = new CountDownLatch(1);

    @Override
    protected void append(ILoggingEvent event) {
      events.add(event);
      if (event.getLoggerName().equals(ApiRequestLogger.class.getName())) {
        requestLogged.countDown();
      }
    }

    ILoggingEvent awaitRequestLine() throws InterruptedException {
      assertTrue(requestLogged.await(2, TimeUnit.SECONDS), "The request was not logged");
      return events.stream()
          .filter(event -> event.getLoggerName().equals(ApiRequestLogger.class.getName()))
          .findFirst()
          .orElseThrow();
    }

    List<String> messages() {
      return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
  }
}
