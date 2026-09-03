package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppConfigLoaderTest {

  private final AppConfigLoader loader = new AppConfigLoader();

  @Test
  void shouldLoadConfiguration() throws IOException {

    AppConfig config = loader.loadFromResource("app-config/valid.properties");

    assertEquals(LlmProvider.OLLAMA, config.provider());
    assertEquals("http://localhost:11434", config.ollama().baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.7, config.generation().temperature());
    assertEquals(2000, config.generation().maxOutputTokens());
    assertEquals(4096, config.ollama().contextWindow());
    assertEquals(1.2, config.ollama().repeatPenalty());
    assertTrue(config.ollama().enabled());
    assertEquals(AppInterface.CONSOLE, config.appInterface());
    assertEquals(8080, config.server().port());
  }

  @Test
  void shouldLoadServerConfiguration() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-http.properties");

    assertEquals(AppInterface.HTTP, config.appInterface());
    assertEquals(7070, config.server().port());
  }

  @Test
  void shouldOverrideServerConfigurationFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader = new AppConfigLoader(this::serverEnvironment);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertEquals(AppInterface.HTTP, config.appInterface());
    assertEquals(9090, config.server().port());
  }

  @Test
  void shouldLoadPublicDemoLimitsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "DEMO_LIMITS_ENABLED" -> "true";
                  case "DEMO_LIMITS_DAILY_REQUESTS" -> "25";
                  case "DEMO_LIMITS_MAX_OUTPUT_TOKENS_PER_CALL" -> "2000";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertTrue(config.demoLimits().enabled());
    assertEquals(25, config.demoLimits().dailyRequests());
  }

  private String serverEnvironment(String key) {
    return switch (key) {
      case "APP_INTERFACE" -> "http";
      case "PORT" -> "9090";
      default -> null;
    };
  }

  @Test
  void shouldLoadDemoLimitProperties() throws IOException {
    DemoLimitsConfig limits =
        loader.loadFromResource("app-config/valid-demo-limits.properties").demoLimits();
    assertTrue(limits.enabled());
    assertEquals(37, limits.dailyRequests());
    assertEquals(9, limits.hourlyRequestsPerIp());
    assertEquals("DEMO_IP_HASH_SALT", limits.ipHashSaltEnvironmentVariable());
  }

  @Test
  void shouldOverrideDemoLimitsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "DEMO_LIMITS_ENABLED" -> "true";
                  case "DEMO_LIMITS_FIRESTORE_ENABLED" -> "true";
                  case "DEMO_LIMITS_DAILY_REQUESTS" -> "73";
                  case "DEMO_LIMITS_HOURLY_REQUESTS_PER_IP" -> "11";
                  case "DEMO_LIMITS_CONCURRENT_STREAMS" -> "2";
                  default -> null;
                });
    DemoLimitsConfig limits =
        environmentLoader.loadFromResource("app-config/valid-demo-limits.properties").demoLimits();
    assertTrue(limits.enabled());
    assertTrue(limits.firestoreEnabled());
    assertEquals(73, limits.dailyRequests());
    assertEquals(11, limits.hourlyRequestsPerIp());
    assertEquals(2, limits.concurrentStreams());
  }

  @Test
  void shouldOverrideOllamaAvailabilityFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OLLAMA_ENABLED".equals(key) ? "false" : null);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid-openai.properties");

    assertFalse(config.ollama().enabled());
  }

  @Test
  void shouldOverrideOllamaConnectionFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "OLLAMA_MODEL" -> "qwen3:8b";
                  case "OLLAMA_BASE_URL" -> "http://ollama:11434";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertEquals("qwen3:8b", config.ollama().model());
    assertEquals("http://ollama:11434", config.ollama().baseUrl());
  }

  @Test
  void shouldRejectDisabledSelectedOllamaProvider() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OLLAMA_ENABLED".equals(key) ? "false" : null);

    assertThrows(
        ConfigurationException.class,
        () -> environmentLoader.loadFromResource("app-config/valid.properties"));
  }

  @Test
  void shouldLoadOpenAiConfiguration() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-openai.properties");

    assertEquals(LlmProvider.OPENAI, config.provider());
    assertEquals("https://api.openai.com/v1", config.openAi().baseUrl());
    assertEquals("OPENAI_API_KEY", config.openAi().apiKeyEnvironmentVariable());
  }

  @Test
  void shouldRejectUnknownProvider() {
    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/unknown-provider.properties"));
  }

  @Test
  void shouldFailWhenResourceDoesNotExist() {

    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/does-not-exist.properties"));
  }

  @Test
  void shouldFailWhenRequiredPropertyIsMissing() {

    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/missing-model.properties"));
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource({
    "app-config/invalid-temperature.properties, temperature",
    "app-config/invalid-num-predict.properties, maxOutputTokens",
    "app-config/invalid-num-ctx.properties, contextWindow",
    "app-config/invalid-repeat-penalty.properties, repeatPenalty"
  })
  void shouldFailForInvalidNumericProperty(String resource, String propertyName) {

    assertThrows(ConfigurationException.class, () -> loader.loadFromResource(resource));
  }
}
