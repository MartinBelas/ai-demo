package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  }

  @Test
  void shouldFailWhenResourceDoesNotExist() {

    assertThrows(
        IllegalStateException.class,
        () -> loader.loadFromResource("app-config/does-not-exist.properties"));
  }

  @Test
  void shouldFailWhenRequiredPropertyIsMissing() {

    assertThrows(
        IllegalStateException.class,
        () -> loader.loadFromResource("app-config/missing-model.properties"));
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource({
    "app-config/invalid-temperature.properties, temperature",
    "app-config/invalid-num-predict.properties, numPredict",
    "app-config/invalid-num-ctx.properties, numCtx"
  })
  void shouldFailForInvalidNumericProperty(String resource, String propertyName) {

    assertThrows(IllegalStateException.class, () -> loader.loadFromResource(resource));
  }
}
