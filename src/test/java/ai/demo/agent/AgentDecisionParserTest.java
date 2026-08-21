package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.AgentDecisionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentDecisionParserTest {

  private final AgentDecisionParser parser = new AgentDecisionParser(new ObjectMapper());

  @Test
  void shouldParseModelReply() {
    AgentDecision decision = parser.parse("{\"type\":\"model_reply\",\"content\":\"Ahoj\"}");

    assertEquals(new ModelReply("Ahoj"), decision);
  }

  @Test
  void shouldParseToolCall() {
    AgentDecision decision =
        parser.parse("{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}");

    assertEquals(new ToolCallDecision("calculator", "2 + 2"), decision);
  }

  @Test
  void shouldParseJsonFromMarkdownBlock() {
    AgentDecision decision =
        parser.parse(
            """
            ```json
            {"type":"model_reply","content":"Ahoj"}
            ```
            """);

    assertEquals(new ModelReply("Ahoj"), decision);
  }

  @Test
  void shouldRejectInvalidJson() {
    AgentDecisionException exception =
        assertThrows(AgentDecisionException.class, () -> parser.parse("not-json"));

    assertEquals("Failed to parse agent decision", exception.getMessage());
  }

  @Test
  void shouldRejectMissingContent() {
    AgentDecisionException exception =
        assertThrows(
            AgentDecisionException.class, () -> parser.parse("{\"type\":\"model_reply\"}"));

    assertEquals("Agent response is missing required field: content", exception.getMessage());
  }
}
