package ai.demo.model.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatResponseTest {

    @Test
    void shouldCreateResponse() {

        var response = new ChatResponse(
                "Answer",
                "qwen3:4b",
                123
        );

        assertEquals("Answer", response.answer());
        assertEquals("qwen3:4b", response.model());
        assertEquals(123, response.durationMs());
    }

    @Test
    void shouldRejectInvalidResponse() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse(null, "model", 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("", "model", 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("   ", "model", 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("answer", null, 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("answer", "", 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("answer", "   ", 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatResponse("answer", "model", -1));
    }
}