package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallingAgentTest {

  @Test
  void shouldReturnDirectAnswerWhenNoToolIsNeeded() {

    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Explain polymorphism"));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");

    Map<String, String> variables = Map.of("tools", "No tools available.");

    when(llmGateway.request(conversation, variables))
        .thenReturn(
            new LlmResponse(
                """
                            {
                              "type": "model_reply",
                              "content": "Polymorphism allows one interface to have multiple implementations."
                            }
                            """,
                "test-model",
                new TokenUsage(10, 20)));

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals(
        "Polymorphism allows one interface to have multiple implementations.", result.answer());

    verify(llmGateway).request(conversation, variables);
  }

  @Test
  void shouldUseToolAndReturnFinalAnswer() {

    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Tool calculator = mock(Tool.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("How much is 125 * 37?"));

    when(calculator.name()).thenReturn("calculator");

    when(toolDescriptionFormatter.format(List.of(calculator)))
        .thenReturn("calculator: Calculates mathematical expressions.");

    Map<String, String> variables =
        Map.of("tools", "calculator: Calculates mathematical expressions.");

    when(llmGateway.request(conversation, variables))
        .thenReturn(
            new LlmResponse(
                """
                            {
                              "type": "tool_call",
                              "toolName": "calculator",
                              "input": "125 * 37"
                            }
                            """,
                "test-model",
                new TokenUsage(10, 10)),
            new LlmResponse(
                """
                            {
                              "type": "model_reply",
                              "content": "125 × 37 = 4625."
                            }
                            """,
                "test-model",
                new TokenUsage(20, 10)));

    when(calculator.execute("125 * 37")).thenReturn(ToolResult.success("4625"));

    Agent agent =
        new ToolCallingAgent(
            llmGateway, toolDescriptionFormatter, List.of(calculator), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("125 × 37 = 4625.", result.answer());

    assertEquals(2, conversation.size());
    assertEquals("calculator", conversation.messages().get(1).toolName());
    assertEquals("4625", conversation.messages().get(1).content());

    verify(calculator).execute("125 * 37");
    verify(llmGateway, times(2)).request(conversation, variables);
  }
}
