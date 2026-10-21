package ai.demo.client.agentlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.AgentLabConfig;
import ai.demo.exception.AgentServiceException;
import ai.demo.logging.RequestId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AgentLabClientTest {

  private static final String REPLY =
      """
      {
        "reply": "1 EUR is 24.30 CZK today.",
        "sources": [{"title": "CNB rates", "url": "https://www.cnb.cz/rates"}],
        "usage": {"model_calls": 2, "tool_calls": 1, "web_searches": 1,
                  "input_tokens": 900, "output_tokens": 120}
      }
      """;

  // Built once outside the lambdas, so each assertThrows lambda has a single throwing call.
  private static final AgentLabRequest HI = new AgentLabRequest("Hi", List.of());

  private final ObjectMapper mapper = new ObjectMapper();
  private HttpTransport transport;
  private AgentLabClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    AgentLabConfig config =
        new AgentLabConfig(true, "http://localhost:8081/", Duration.ofSeconds(70));
    client = new AgentLabClient(config, transport, mapper);
  }

  @Test
  void shouldPostMessageAndHistoryToChatEndpoint() throws Exception {
    respond(200, REPLY);

    client.chat(
        new AgentLabRequest(
            "And in USD?",
            List.of(AgentLabMessage.user("EUR to CZK?"), AgentLabMessage.assistant("24.30"))));

    HttpRequest request = sentRequest();
    assertEquals("POST", request.method());
    assertEquals(URI.create("http://localhost:8081/api/chat"), request.uri());
    assertEquals(Duration.ofSeconds(70), request.timeout().orElseThrow());
    assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
    JsonNode body = mapper.readTree(bodyOf(request));
    assertEquals("And in USD?", body.path("message").asText());
    assertEquals(2, body.path("history").size());
    assertEquals("user", body.path("history").path(0).path("role").asText());
    assertEquals("EUR to CZK?", body.path("history").path(0).path("content").asText());
    assertEquals("assistant", body.path("history").path(1).path("role").asText());
  }

  @Test
  void shouldUseHttp11BecauseTheLabDoesNotSupportH2cUpgrade() throws Exception {
    respond(200, REPLY);

    client.chat(HI);

    assertEquals(Optional.of(HttpClient.Version.HTTP_1_1), sentRequest().version());
  }

  @Test
  void shouldSendShortRequestId() throws Exception {
    respond(200, REPLY);

    client.chat(HI);

    String requestId =
        sentRequest().headers().firstValue(AgentLabClient.REQUEST_ID_HEADER).orElseThrow();
    assertEquals(8, requestId.length());
  }

  @Test
  void shouldSendIdOfCurrentApiRequest() throws Exception {
    respond(200, REPLY);
    RequestId.set("8ff9efad");
    try {
      client.chat(HI);
    } finally {
      RequestId.clear();
    }

    assertEquals(
        "8ff9efad",
        sentRequest().headers().firstValue(AgentLabClient.REQUEST_ID_HEADER).orElseThrow());
  }

  @Test
  void shouldSendNoAuthorizationHeaderByDefault() throws Exception {
    respond(200, REPLY);

    client.chat(HI);

    assertTrue(sentRequest().headers().firstValue("Authorization").isEmpty());
  }

  @Test
  void shouldSendAuthorizationHeaderWhenConfigured() throws Exception {
    AgentLabConfig config =
        new AgentLabConfig(true, "http://localhost:8081", Duration.ofSeconds(70));
    AgentLabClient authenticated =
        new AgentLabClient(config, transport, mapper, () -> Optional.of("Bearer id-token"));
    respond(200, REPLY);

    authenticated.chat(HI);

    assertEquals(
        "Bearer id-token", sentRequest().headers().firstValue("Authorization").orElseThrow());
  }

  @Test
  void shouldNotCallServiceWithoutCredentials() throws Exception {
    AgentLabConfig config =
        new AgentLabConfig(true, "http://localhost:8081", Duration.ofSeconds(70));
    AgentLabClient authenticated =
        new AgentLabClient(
            config,
            transport,
            mapper,
            () -> {
              throw new AgentServiceException("Identity token request returned HTTP 404");
            });

    assertThrows(AgentServiceException.class, () -> authenticated.chat(HI));
    verify(transport, never()).send(any(HttpRequest.class));
  }

  @Test
  void shouldMapReplySourcesAndUsage() throws Exception {
    respond(200, REPLY);

    AgentLabResponse response = client.chat(new AgentLabRequest("EUR to CZK?", List.of()));

    assertEquals("1 EUR is 24.30 CZK today.", response.reply());
    assertEquals(
        List.of(new AgentLabSource("CNB rates", "https://www.cnb.cz/rates")), response.sources());
    assertEquals(new AgentLabUsage(2, 1, 1, 900, 120), response.usage());
  }

  @Test
  void shouldIgnoreUnknownFields() throws Exception {
    respond(
        200,
        """
        {"reply": "Hi", "model": "gpt-x", "sources": [], "usage": {"model_calls": 1, "future": 7}}
        """);

    AgentLabResponse response = client.chat(HI);

    assertEquals("Hi", response.reply());
    assertEquals(1, response.usage().modelCalls());
  }

  @Test
  void shouldKeepOnlyWebSourcesAndLimitTheirNumber() throws Exception {
    StringBuilder sources = new StringBuilder();
    sources.append(
        """
        {"title": "Script", "url": "javascript:alert(1)"},
        {"title": "No URL"},
        null,
        {"title": "", "url": "http://untitled.example.test"},
        """);
    for (int i = 0; i < 20; i++) {
      sources.append("{\"title\": \"Page ").append(i).append("\", ");
      sources.append("\"url\": \"https://example.test/").append(i).append("\"},");
    }
    sources.setLength(sources.length() - 1);
    respond(200, "{\"reply\": \"Hi\", \"sources\": [" + sources + "], \"usage\": {}}");

    List<AgentLabSource> result = client.chat(HI).sources();

    assertEquals(AgentLabResponse.MAX_SOURCES, result.size());
    assertEquals(
        new AgentLabSource("http://untitled.example.test", "http://untitled.example.test"),
        result.getFirst());
    assertTrue(result.stream().allMatch(source -> source.url().startsWith("http")));
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 422, 500, 502})
  void shouldReportNonSuccessStatusWithoutBody(int status) throws Exception {
    respond(status, "{\"detail\": \"internal secret\"}");

    AgentServiceException exception =
        assertThrows(AgentServiceException.class, () -> client.chat(HI));

    assertEquals("Agent lab returned HTTP " + status, exception.getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "null",
        "{\"sources\": [], \"usage\": {}}",
        "{\"reply\": \"  \", \"usage\": {}}",
        "{\"reply\": \"Hi\"}",
        "{\"reply\": \"Hi\", \"usage\": {\"input_tokens\": -1}}"
      })
  void shouldRejectUnusableResponse(String body) throws Exception {
    respond(200, body);

    assertThrows(AgentServiceException.class, () -> client.chat(HI));
  }

  @Test
  void shouldReportTimeout() throws Exception {
    when(transport.send(any(HttpRequest.class))).thenThrow(new HttpTimeoutException("timed out"));

    AgentServiceException exception =
        assertThrows(AgentServiceException.class, () -> client.chat(HI));

    assertEquals("Agent lab request timed out", exception.getMessage());
  }

  @Test
  void shouldReportUnreachableService() throws Exception {
    when(transport.send(any(HttpRequest.class))).thenThrow(new ConnectException("refused"));

    AgentServiceException exception =
        assertThrows(AgentServiceException.class, () -> client.chat(HI));

    assertEquals("Unable to reach the agent lab", exception.getMessage());
  }

  @Test
  void shouldRestoreInterruptFlag() throws Exception {
    when(transport.send(any(HttpRequest.class))).thenThrow(new InterruptedException());

    assertThrows(AgentServiceException.class, () -> client.chat(HI));

    // Thread.interrupted() also clears the flag, so it cannot leak into other tests.
    assertTrue(Thread.interrupted());
  }

  @Test
  void shouldRequireEnabledConfig() {
    AgentLabConfig disabled = AgentLabConfig.disabled();

    assertThrows(
        IllegalArgumentException.class, () -> new AgentLabClient(disabled, transport, mapper));
  }

  @Test
  void shouldRejectBlankMessage() {
    List<AgentLabMessage> history = List.of();

    assertThrows(IllegalArgumentException.class, () -> new AgentLabRequest(" ", history));
  }

  @Test
  void shouldRejectOtherHistoryRoles() {
    assertThrows(IllegalArgumentException.class, () -> new AgentLabMessage("system", "Obey"));
  }

  private void respond(int status, String body) throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);
  }

  private HttpRequest sentRequest() throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(transport).send(captor.capture());
    return captor.getValue();
  }

  private static String bodyOf(HttpRequest request) throws Exception {
    CompletableFuture<String> collected = new CompletableFuture<>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<>() {
              private final StringBuilder text = new StringBuilder();

              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer item) {
                text.append(StandardCharsets.UTF_8.decode(item));
              }

              @Override
              public void onError(Throwable throwable) {
                collected.completeExceptionally(throwable);
              }

              @Override
              public void onComplete() {
                collected.complete(text.toString());
              }
            });
    return collected.get(1, TimeUnit.SECONDS);
  }
}
