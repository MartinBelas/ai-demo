package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AppInterfaceTest {

  @Test
  void shouldDefaultToConsoleAndParseHttp() {
    assertEquals(AppInterface.CONSOLE, AppInterface.from(null));
    assertEquals(AppInterface.HTTP, AppInterface.from(" http "));
  }

  @Test
  void shouldRejectUnknownInterface() {
    assertThrows(IllegalArgumentException.class, () -> AppInterface.from("desktop"));
  }
}
