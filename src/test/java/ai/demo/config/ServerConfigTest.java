package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerConfigTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 65536})
  void shouldRejectInvalidPort(int port) {
    assertThrows(IllegalArgumentException.class, () -> new ServerConfig(port));
  }
}
