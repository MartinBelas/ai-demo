package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  private String serverEnvironment(String key) {
    return switch (key) {
      case "APP_INTERFACE" -> "http";
      case "PORT" -> "9090";
      default -> null;
    };
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
