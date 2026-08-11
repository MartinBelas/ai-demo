package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  @Test
  void shouldCreateConfiguration() {

    var config =
        new AppConfig(
            "http://localhost:11434",
            "qwen3:4b",
            0.5,
            2000,
            4096,
            1.2,
            Path.of("conversation.json"));

    assertEquals("http://localhost:11434", config.baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.5, config.temperature());
    assertEquals(2000, config.numPredict());
    assertEquals(4096, config.numCtx());
    assertEquals(1.2, config.repeatPenalty());
    assertEquals(Path.of("conversation.json"), config.conversationFile());
  }

  @Test
  void shouldRejectInvalidConfiguration() {

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig(null, "model", 0.5, 100, 100, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", null, 0.5, 100, 100, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", -1.0, 100, 100, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", 3.0, 100, 100, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", 0.5, 0, 100, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", 0.5, 100, 0, 1.2, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", 0.5, 100, 100, 0.5, Path.of("x")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("url", "model", 0.5, 100, 100, 1.2, null));
  }
}
