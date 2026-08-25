package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AppInterfaceTest {

  @Test
  void shouldDefaultToConsoleAndParseServer() {
    assertEquals(AppInterface.CONSOLE, AppInterface.from(null));
    assertEquals(AppInterface.SERVER, AppInterface.from(" server "));
  }

  @Test
  void shouldRejectUnknownInterface() {
    assertThrows(IllegalArgumentException.class, () -> AppInterface.from("desktop"));
  }
}
