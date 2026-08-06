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

    assertEquals("http://localhost:11434", config.baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.7, config.temperature());
    assertEquals(2000, config.numPredict());
    assertEquals(4096, config.numCtx());
    assertEquals(1.2, config.repeatPenalty());
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
    "app-config/invalid-max-tokens.properties, numPredict",
    "app-config/invalid-num-ctx.properties, numCtx",
    "app-config/invalid-repeat-penalty.properties, repeatPenalty"
  })
  void shouldFailForInvalidNumericProperty(String resource, String propertyName) {

    assertThrows(ConfigurationException.class, () -> loader.loadFromResource(resource));
  }
}
