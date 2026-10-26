package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentLabConfigTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(70);

  @Test
  void shouldBeDisabledByDefault() {
    AgentLabConfig config = AgentLabConfig.disabled();

    assertFalse(config.enabled());
    assertNull(config.baseUrl());
    assertEquals(AgentLabAuthentication.NONE, config.authentication());
  }

  @Test
  void shouldNotRequireBaseUrlWhenDisabled() {
    AgentLabConfig config = new AgentLabConfig(false, "not a url", TIMEOUT);

    assertFalse(config.enabled());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"http://localhost:8081", "http://localhost:8081/", " http://localhost:8081// "})
  void shouldNormalizeBaseUrl(String baseUrl) {
    AgentLabConfig config = new AgentLabConfig(true, baseUrl, TIMEOUT);

    assertEquals("http://localhost:8081", config.baseUrl());
  }

  @Test
  void shouldKeepPathPrefixOfBaseUrl() {
    AgentLabConfig config = new AgentLabConfig(true, "https://agent.example.test/lab/", TIMEOUT);

    assertEquals("https://agent.example.test/lab", config.baseUrl());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "localhost:8081",
        "ftp://localhost:8081",
        "http://user:secret@localhost:8081",
        "http://localhost:8081?debug=true",
        "http://localhost:8081#fragment",
        "http://"
      })
  void shouldRejectInvalidBaseUrlWhenEnabled(String baseUrl) {
    assertThrows(IllegalArgumentException.class, () -> new AgentLabConfig(true, baseUrl, TIMEOUT));
  }

  @Test
  void shouldRejectMissingBaseUrlWhenEnabled() {
    assertThrows(IllegalArgumentException.class, () -> new AgentLabConfig(true, null, TIMEOUT));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 301})
  void shouldRejectTimeoutOutsideAllowedRange(long seconds) {
    Duration timeout = Duration.ofSeconds(seconds);

    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentLabConfig(true, "http://localhost:8081", timeout));
  }

  @Test
  void shouldRejectMissingTimeout() {
    assertThrows(IllegalArgumentException.class, () -> new AgentLabConfig(false, null, null));
  }
}
