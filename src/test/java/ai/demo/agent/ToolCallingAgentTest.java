package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.exception.AgentDecisionException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
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

    doAnswer(
        invocation -> {
          java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
          consumer.accept(
              new ChatChunk(
                  "{\"type\":\"model_reply\",\"content\":\"Polymorphism allows one interface to have multiple implementations.\"}",
                  ChatChunkType.CONTENT,
                  true));
          return new StreamingResult("test-model", new TokenUsage(10, 20));
        })
        .when(llmGateway)
        .stream(eq(conversation), eq(variables), any());

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals(
        "Polymorphism allows one interface to have multiple implementations.", result.answer());

    verify(llmGateway).stream(eq(conversation), eq(variables), any());
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

    doAnswer(
        new org.mockito.stubbing.Answer<StreamingResult>() {
          private int call;

          @Override
          public StreamingResult answer(org.mockito.invocation.InvocationOnMock invocation) {
            java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
            if (call++ == 0) {
              consumer.accept(
                  new ChatChunk(
                      "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"125 * 37\"}",
                      ChatChunkType.CONTENT,
                      true));
              return new StreamingResult("test-model", new TokenUsage(10, 10));
            }

            assertEquals(3, conversation.size());
            assertEquals(Role.ASSISTANT, conversation.messages().get(1).role());
            assertEquals(
                "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"125 * 37\"}",
                conversation.messages().get(1).content());
            assertEquals(Role.TOOL, conversation.messages().get(2).role());
            assertEquals("calculator", conversation.messages().get(2).toolName());
            assertEquals("4625", conversation.messages().get(2).content());

            consumer.accept(
                new ChatChunk(
                    "{\"type\":\"model_reply\",\"content\":\"125 × 37 = 4625.\"}",
                    ChatChunkType.CONTENT,
                    true));
            return new StreamingResult("test-model", new TokenUsage(20, 10));
          }
        })
        .when(llmGateway)
        .stream(eq(conversation), eq(variables), any());

    when(calculator.execute("125 * 37")).thenReturn(ToolResult.success("4625"));

    Agent agent =
        new ToolCallingAgent(
            llmGateway, toolDescriptionFormatter, List.of(calculator), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("125 × 37 = 4625.", result.answer());

    assertEquals(3, conversation.size());

    verify(calculator).execute("125 * 37");
    verify(llmGateway, times(2)).stream(eq(conversation), eq(variables), any());
  }

  @Test
  void shouldRepairInvalidDecisionOnce() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Say hello"));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");
    doAnswer(
        new org.mockito.stubbing.Answer<StreamingResult>() {
          private int call;

          @Override
          public StreamingResult answer(org.mockito.invocation.InvocationOnMock invocation) {
            java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
            if (call++ == 0) {
              consumer.accept(new ChatChunk("not-json", ChatChunkType.CONTENT, true));
              return new StreamingResult("test-model", new TokenUsage(10, 5));
            }

            consumer.accept(
                new ChatChunk(
                    "{\"type\":\"model_reply\",\"content\":\"Hello\"}",
                    ChatChunkType.CONTENT,
                    true));
            return new StreamingResult("test-model", new TokenUsage(12, 6));
          }
        })
        .when(llmGateway)
        .stream(any(), any(), any());

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("Hello", result.answer());
    assertEquals(new TokenUsage(22, 11), result.tokenUsage());
    verify(llmGateway, times(2)).stream(any(), any(), any());
  }

  @Test
  void shouldFailAfterOneUnsuccessfulRepair() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Say hello"));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");
    doAnswer(
        invocation -> {
          java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
          consumer.accept(new ChatChunk("not-json", ChatChunkType.CONTENT, true));
          return new StreamingResult("test-model", new TokenUsage(10, 5));
        })
        .when(llmGateway)
        .stream(any(), any(), any());

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    assertThrows(AgentDecisionException.class, () -> agent.execute(conversation));
    verify(llmGateway, times(2)).stream(any(), any(), any());
  }

  @Test
  void shouldReportEmptyStreamAsLlmCommunicationFailure() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is Spring?"));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");
    when(llmGateway.stream(any(), any(), any()))
        .thenReturn(new StreamingResult("test-model", new TokenUsage(552, 300)));

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> agent.execute(conversation));

    assertEquals(
        "The model returned no response content. It may have exhausted the output token limit"
            + " while reasoning.",
        exception.getMessage());
  }
}
