package ai.demo.model.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.client.TokenUsage;
import org.junit.jupiter.api.Test;

class ChatResponseTest {

  @Test
  void shouldCreateResponse() {

    var response = new ChatResponse("Answer", "qwen3:4b", new TokenUsage(10, 5), 1234L);

    assertEquals("Answer", response.answer());
    assertEquals("qwen3:4b", response.model());
    assertEquals(new TokenUsage(10, 5), response.tokenUsage());
    assertEquals(1234L, response.durationMs());
  }

  @Test
  void shouldRejectInvalidResponse() {

    TokenUsage usage = new TokenUsage(1, 1);

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse(null, "model", usage, 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("", "model", usage, 100L));

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse("   ", "model", usage, 100L));

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse("answer", null, usage, 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("answer", "", usage, 100L));

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse("answer", "   ", usage, 100L));

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse("answer", "model", null, 100L));

    assertThrows(
        IllegalArgumentException.class, () -> new ChatResponse("answer", "model", usage, -1L));
  }
}
