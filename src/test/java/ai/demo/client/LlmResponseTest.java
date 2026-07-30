package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LlmResponseTest {

  @Test
  void shouldCreateResponse() {

    var response = new LlmResponse("Test text", "test-model");

    assertEquals("Test text", response.text());
    assertEquals("test-model", response.model());
  }

  @Test
  void shouldRejectInvalidResponse() {

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse(null, "model"));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("", "model"));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("   ", "model"));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", null));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", ""));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", "   "));
  }
}
