package ai.demo.client.agentlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.exception.AgentServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class GoogleIdentityTokenAuthorizationTest {

  private static final String SERVICE_URL = "https://ai-agent-lab-123.europe-west1.run.app/lab";
  private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

  private HttpTransport transport;
  private MutableClock clock;
  private GoogleIdentityTokenAuthorization authorization;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    clock = new MutableClock(NOW);
    authorization =
        new GoogleIdentityTokenAuthorization(SERVICE_URL, transport, new ObjectMapper(), clock);
  }

  @Test
  void shouldRequestTokenFromMetadataServerForServiceAudience() throws Exception {
    respond(200, jwtExpiringAt(NOW.plusSeconds(3600)));

    authorization.header();

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(transport).send(captor.capture());
    HttpRequest request = captor.getValue();
    assertEquals("metadata.google.internal", request.uri().getHost());
    assertEquals(
        "audience=https%3A%2F%2Fai-agent-lab-123.europe-west1.run.app",
        request.uri().getRawQuery());
    assertEquals("Google", request.headers().firstValue("Metadata-Flavor").orElseThrow());
  }

  @Test
  void shouldReturnBearerToken() throws Exception {
    String token = jwtExpiringAt(NOW.plusSeconds(3600));
    respond(200, token + "\n");

    assertEquals("Bearer " + token, authorization.header().orElseThrow());
  }

  @Test
  void shouldReuseTokenUntilShortlyBeforeExpiry() throws Exception {
    respond(200, jwtExpiringAt(NOW.plusSeconds(3600)));

    authorization.header();
    clock.now = NOW.plusSeconds(3600 - 301);
    authorization.header();
    verify(transport, times(1)).send(any(HttpRequest.class));

    clock.now = NOW.plusSeconds(3600 - 299);
    authorization.header();
    verify(transport, times(2)).send(any(HttpRequest.class));
  }

  @Test
  void shouldReportMetadataServerError() throws Exception {
    respond(404, "Not Found");

    assertThrows(AgentServiceException.class, () -> authorization.header());
  }

  @Test
  void shouldReportUnreachableMetadataServer() throws Exception {
    when(transport.send(any(HttpRequest.class))).thenThrow(new ConnectException("no metadata"));

    assertThrows(AgentServiceException.class, () -> authorization.header());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-jwt", "a.b.c", "a.eyJzdWIiOiJ4In0.c"})
  void shouldRejectMalformedToken(String token) throws Exception {
    respond(200, token);

    assertThrows(AgentServiceException.class, () -> authorization.header());
  }

  @Test
  void shouldUseServiceRootAsAudience() {
    assertEquals(
        "http://localhost:8081",
        GoogleIdentityTokenAuthorization.audience("http://localhost:8081/x"));
  }

  private void respond(int status, String body) throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);
  }

  private static String jwtExpiringAt(Instant expiry) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    String claims =
        encoder.encodeToString(
            ("{\"exp\":" + expiry.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
    return header + "." + claims + ".signature";
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
