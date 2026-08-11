package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LlmResponseTest {

  @Test
  void shouldCreateResponse() {

    TokenUsage tokenUsage = new TokenUsage(100, 50);

    var response = new LlmResponse("Test text", "test-model", tokenUsage);

    assertEquals("Test text", response.text());
    assertEquals("test-model", response.model());
    assertEquals(tokenUsage, response.tokenUsage());
  }

  @Test
  void shouldRejectInvalidResponse() {

    TokenUsage tokenUsage = new TokenUsage(100, 50);

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse(null, "model", tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("", "model", tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("   ", "model", tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", null, tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", "", tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", "   ", tokenUsage));

    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", "model", null));
  }
}
