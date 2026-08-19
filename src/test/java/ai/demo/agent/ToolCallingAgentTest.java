package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallingAgentTest {

  @Test
  void shouldReturnDirectAnswerWhenNoToolIsNeeded() {

    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer = mock(PromptComposer.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Explain polymorphism"));

    Prompt prompt = new Prompt(List.of(ChatMessage.user("Explain polymorphism")));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");

    when(promptComposer.compose(conversation, Map.of("tools", "No tools available.")))
        .thenReturn(prompt);

    when(llmClient.chat(prompt))
        .thenReturn(
            new LlmResponse(
                """
                            {
                              "tool": null,
                              "input": null,
                              "answer": "Polymorphism allows one interface to have multiple implementations."
                            }
                            """,
                "test-model",
                new TokenUsage(10, 20)));

    Agent agent =
        new ToolCallingAgent(
            llmClient, promptComposer, toolDescriptionFormatter, List.of(), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals(
        "Polymorphism allows one interface to have multiple implementations.", result.answer());
  }

  @Test
  void shouldUseToolAndReturnFinalAnswer() {

    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer = mock(PromptComposer.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Tool calculator = mock(Tool.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("How much is 125 * 37?"));

    Prompt initialPrompt = new Prompt(List.of(ChatMessage.user("How much is 125 * 37?")));

    Prompt finalPrompt =
        new Prompt(
            List.of(
                ChatMessage.user("How much is 125 * 37?"), ChatMessage.tool("calculator", "4625")));

    when(calculator.name()).thenReturn("calculator");

    when(toolDescriptionFormatter.format(List.of(calculator)))
        .thenReturn("calculator: Calculates mathematical expressions.");

    Map<String, String> variables =
        Map.of("tools", "calculator: Calculates mathematical expressions.");

    when(promptComposer.compose(conversation, variables)).thenReturn(initialPrompt, finalPrompt);

    when(llmClient.chat(initialPrompt))
        .thenReturn(
            new LlmResponse(
                """
                            {
                              "tool": "calculator",
                              "input": "125 * 37",
                              "answer": null
                            }
                            """,
                "test-model",
                new TokenUsage(10, 10)));

    when(calculator.execute("125 * 37")).thenReturn(ToolResult.success("4625"));

    when(llmClient.chat(finalPrompt))
        .thenReturn(
            new LlmResponse(
                """
                            {
                              "tool": null,
                              "input": null,
                              "answer": "125 × 37 = 4625."
                            }
                            """,
                "test-model",
                new TokenUsage(20, 10)));

    Agent agent =
        new ToolCallingAgent(
            llmClient,
            promptComposer,
            toolDescriptionFormatter,
            List.of(calculator),
            new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("125 × 37 = 4625.", result.answer());

    verify(calculator).execute("125 * 37");
    verify(llmClient).chat(initialPrompt);
    verify(llmClient).chat(finalPrompt);
  }
}
