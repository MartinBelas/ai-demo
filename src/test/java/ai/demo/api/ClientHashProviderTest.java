package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.exception.ConfigurationException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class ClientHashProviderTest {

  private static final String PEER_ADDRESS = "10.0.0.1";
  private static final String FORWARDED_FOR = "X-Forwarded-For";

  @ParameterizedTest(name = "hops={0}, header=\"{1}\" -> {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        // Cloud Run appends the address it saw, so the rightmost entry is the real client.
        "1 | 203.0.113.7 | 203.0.113.7",
        "1 | 198.51.100.1, 203.0.113.7 | 203.0.113.7",
        // Behind an external load balancer, the balancer's own address follows the client's.
        "2 | 198.51.100.1, 203.0.113.7, 35.191.0.1 | 203.0.113.7",
        // Fewer entries than trusted proxies: the header did not pass all proxies.
        "2 | 203.0.113.7 | 10.0.0.1",
        // Zero trusted proxies: the header is ignored entirely.
        "0 | 203.0.113.7 | 10.0.0.1",
        // A trailing empty entry still counts as an entry, here the blank one the proxy appended.
        "1 | 203.0.113.7, | 10.0.0.1",
        "2 | 203.0.113.7, 198.51.100.1, | 198.51.100.1",
      })
  void shouldUseTheEntryAddedByTheOutermostTrustedProxy(
      int trustedProxyHops, String forwardedFor, String expectedAddress) {
    ClientHashProvider provider = new ClientHashProvider("salt", trustedProxyHops);

    assertEquals(expectedAddress, provider.clientAddress(request(forwardedFor)));
  }

  @Test
  void shouldUsePeerAddressWithoutForwardedHeader() {
    ClientHashProvider provider = new ClientHashProvider("salt", 1);

    assertEquals(PEER_ADDRESS, provider.clientAddress(requestWithoutForwardedHeader()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldUsePeerAddressForBlankForwardedHeader(String forwardedFor) {
    ClientHashProvider provider = new ClientHashProvider("salt", 1);

    assertEquals(PEER_ADDRESS, provider.clientAddress(request(forwardedFor)));
  }

  @Test
  void shouldRejectNegativeTrustedProxyHops() {
    assertThrows(ConfigurationException.class, () -> new ClientHashProvider("salt", -1));
  }

  @Test
  void shouldWarnOnceWhenForwardedHeaderIsIgnored() {
    ClientHashProvider provider = new ClientHashProvider("salt", 0);

    List<ILoggingEvent> events =
        capturedWarnings(
            () -> {
              provider.clientAddress(request("203.0.113.7"));
              provider.clientAddress(request("198.51.100.1"));
            });

    assertEquals(1, events.size(), events.toString());
    assertTrue(
        events.getFirst().getFormattedMessage().contains("demo.limits.trusted-proxy-hops"),
        events.getFirst().getFormattedMessage());
  }

  @Test
  void shouldNotWarnWithoutForwardedHeaderWhenHeaderIsIgnored() {
    ClientHashProvider provider = new ClientHashProvider("salt", 0);

    List<ILoggingEvent> events =
        capturedWarnings(() -> provider.clientAddress(requestWithoutForwardedHeader()));

    assertTrue(events.isEmpty(), events.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.2.3.4", "5.6.7.8, 9.9.9.9"})
  void shouldGiveTheSameHashWhateverTheClientPrepends(String spoofedPrefix) {
    ClientHashProvider provider = new ClientHashProvider("salt", 1);

    String honest = provider.hash(request("203.0.113.7"));
    String spoofed = provider.hash(request(spoofedPrefix + ", 203.0.113.7"));

    assertEquals(honest, spoofed);
  }

  @Test
  void shouldTreatRepeatedHeaderLinesAsOneCommaSeparatedList() {
    ClientHashProvider provider = new ClientHashProvider("salt", 1);

    assertEquals(
        "203.0.113.7", provider.clientAddress(request("1.2.3.4", "5.6.7.8", "203.0.113.7")));
  }

  @Test
  void shouldGiveTheSameHashWhateverHeaderLinesTheClientPrepends() {
    ClientHashProvider provider = new ClientHashProvider("salt", 1);

    assertEquals(
        provider.hash(request("203.0.113.7")), provider.hash(request("1.2.3.4", "203.0.113.7")));
  }

  @Test
  void shouldDependOnTheSecretSalt() {
    Context request = request("203.0.113.7");

    assertNotEquals(
        new ClientHashProvider("salt", 1).hash(request),
        new ClientHashProvider("other-salt", 1).hash(request));
  }

  private static Context requestWithoutForwardedHeader() {
    return request();
  }

  /** A request with one {@code X-Forwarded-For} line per argument, in order. */
  private static Context request(String... forwardedForLines) {
    Context context = mock(Context.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getHeaders(FORWARDED_FOR))
        .thenAnswer(invocation -> Collections.enumeration(List.of(forwardedForLines)));
    when(context.header(FORWARDED_FOR))
        .thenReturn(forwardedForLines.length == 0 ? null : forwardedForLines[0]);
    when(context.req()).thenReturn(servletRequest);
    when(context.ip()).thenReturn(PEER_ADDRESS);
    return context;
  }

  private static List<ILoggingEvent> capturedWarnings(Runnable action) {
    Logger logger = (Logger) LoggerFactory.getLogger(ClientHashProvider.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
    return appender.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
        .toList();
  }
}
