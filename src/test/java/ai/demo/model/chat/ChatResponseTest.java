package ai.demo.model.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChatResponseTest {

  @Test
  void shouldCreateResponse() {

    var response = new ChatResponse("Answer", "qwen3:4b", 1234L);

    assertEquals("Answer", response.answer());
    assertEquals("qwen3:4b", response.model());
    assertEquals(1234L, response.durationInSeconds());
  }

  @Test
  void shouldRejectInvalidResponse() {

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse(null, "model", 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("", "model", 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("   ", "model", 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("answer", null, 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("answer", "", 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("answer", "   ", 100L));

    assertThrows(IllegalArgumentException.class, () -> new ChatResponse("answer", "model", -1L));
  }
}
