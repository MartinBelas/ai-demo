package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolCallingAgentTest {

  @Test
  void shouldUseResolvedToolWithoutCallingLlm() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Tool calculator = mock(Tool.class);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("3+3"));
    when(calculator.resolveInput("3+3")).thenReturn(Optional.of("3+3"));
    when(calculator.name()).thenReturn("calculator");
    when(calculator.execute("3+3")).thenReturn(ToolResult.success("3 + 3 = 6"));
    ArrayList<AgentEvent> events = new ArrayList<>();

    Agent agent =
        new ToolCallingAgent(
            llmGateway, toolDescriptionFormatter, List.of(calculator), new ObjectMapper());

    AgentResult result = agent.execute(conversation, events::add);

    assertEquals("3 + 3 = 6", result.answer());
    assertEquals("calculator", result.model());
    assertEquals(new TokenUsage(0, 0), result.tokenUsage());
    assertEquals(
        List.of(
            new ToolCallEvent("calculator", "3+3"),
            new ToolResultEvent("calculator", "3 + 3 = 6"),
            new ContentEvent("3 + 3 = 6")),
        events);
    verifyNoInteractions(llmGateway, toolDescriptionFormatter);
  }

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
  void shouldReturnFinalToolResultWithoutAnotherLlmCall() {

    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Tool calculator = mock(Tool.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("How much is 125 * 37?"));

    when(calculator.name()).thenReturn("calculator");
    when(calculator.resultIsFinal()).thenReturn(true);

    when(toolDescriptionFormatter.format(List.of(calculator)))
        .thenReturn("calculator: Calculates mathematical expressions.");

    Map<String, String> variables =
        Map.of("tools", "calculator: Calculates mathematical expressions.");

    doAnswer(
        invocation -> {
          java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
          consumer.accept(
              new ChatChunk(
                  "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"125 * 37\"}",
                  ChatChunkType.CONTENT,
                  true));
          return new StreamingResult("test-model", new TokenUsage(10, 10));
        })
        .when(llmGateway)
        .stream(eq(conversation), eq(variables), any());

    when(calculator.execute("125 * 37")).thenReturn(ToolResult.success("125 * 37 = 4625"));

    Agent agent =
        new ToolCallingAgent(
            llmGateway, toolDescriptionFormatter, List.of(calculator), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("125 * 37 = 4625", result.answer());
    assertEquals(new TokenUsage(10, 10), result.tokenUsage());
    assertEquals(1, conversation.size());

    verify(calculator).execute("125 * 37");
    verify(llmGateway).stream(eq(conversation), eq(variables), any());
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
    verify(llmGateway, times(2)).stream(any(), any(), any());
  }

  @Test
  void shouldRetryWhenFirstStreamContainsOnlyThinking() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter toolDescriptionFormatter = mock(ToolDescriptionFormatter.class);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("dva plus sedm"));

    when(toolDescriptionFormatter.format(List.of())).thenReturn("No tools available.");
    doAnswer(
        new org.mockito.stubbing.Answer<StreamingResult>() {
          private int call;

          @Override
          public StreamingResult answer(org.mockito.invocation.InvocationOnMock invocation) {
            java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
            if (call++ == 0) {
              consumer.accept(new ChatChunk("Still deciding", ChatChunkType.THINKING, false));
              return new StreamingResult("test-model", new TokenUsage(10, 5));
            }
            consumer.accept(
                new ChatChunk(
                    "{\"type\":\"model_reply\",\"content\":\"9\"}", ChatChunkType.CONTENT, true));
            return new StreamingResult("test-model", new TokenUsage(12, 4));
          }
        })
        .when(llmGateway)
        .stream(any(), any(), any());

    Agent agent =
        new ToolCallingAgent(llmGateway, toolDescriptionFormatter, List.of(), new ObjectMapper());

    AgentResult result = agent.execute(conversation);

    assertEquals("9", result.answer());
    assertEquals(new TokenUsage(22, 9), result.tokenUsage());
    verify(llmGateway, times(2)).stream(any(), any(), any());
  }

  @Test
  void shouldNotModifyCallersConversationWhenCallingTool() {
    AgentLlmGateway llmGateway =
        scriptedGateway(
            "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}",
            "{\"type\":\"model_reply\",\"content\":\"It is 4.\"}");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is two plus two?"));

    AgentResult result = agent(llmGateway, calculator()).execute(conversation);

    assertEquals("It is 4.", result.answer());
    assertEquals(List.of(ChatMessage.user("What is two plus two?")), conversation.messages());
  }

  @Test
  void shouldRepairUnknownToolName() {
    List<Conversation> requests = new ArrayList<>();
    AgentLlmGateway llmGateway =
        scriptedGateway(
            requests,
            "{\"type\":\"tool_call\",\"toolName\":\"weather\",\"input\":\"Prague\"}",
            "{\"type\":\"model_reply\",\"content\":\"I cannot check the weather.\"}");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Weather in Prague?"));

    AgentResult result = agent(llmGateway, calculator()).execute(conversation);

    assertEquals("I cannot check the weather.", result.answer());
    String repairRequest = requests.get(1).messages().getLast().content();
    assertTrue(repairRequest.contains("Unknown tool: weather"), repairRequest);
    assertTrue(repairRequest.contains("Available tools: calculator"), repairRequest);
  }

  @Test
  void shouldTellModelThatNoToolsAreAvailable() {
    List<Conversation> requests = new ArrayList<>();
    AgentLlmGateway llmGateway =
        scriptedGateway(
            requests,
            "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}",
            "{\"type\":\"model_reply\",\"content\":\"I have no tools.\"}");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is two plus two?"));

    AgentResult result = agent(llmGateway).execute(conversation);

    assertEquals("I have no tools.", result.answer());
    String repairRequest = requests.get(1).messages().getLast().content();
    assertTrue(repairRequest.contains("Available tools: none"), repairRequest);
  }

  @Test
  void shouldFailWithDecisionErrorWhenToolStaysUnknownAfterRepair() {
    String unknownTool = "{\"type\":\"tool_call\",\"toolName\":\"weather\",\"input\":\"Prague\"}";
    AgentLlmGateway llmGateway = scriptedGateway(unknownTool, unknownTool);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Weather in Prague?"));
    Agent agent = agent(llmGateway, calculator());

    List<AgentEvent> events = new ArrayList<>();

    assertThrows(AgentDecisionException.class, () -> agent.execute(conversation, events::add));
    assertEquals(4, reportedTokens(events));
  }

  @Test
  void shouldReportTokensOfBothAttemptsWhenDecisionStaysInvalidAfterRepair() {
    AgentLlmGateway llmGateway = scriptedGateway("not json", "still not json");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));
    Agent agent = agent(llmGateway, calculator());

    List<AgentEvent> events = new ArrayList<>();

    assertThrows(AgentDecisionException.class, () -> agent.execute(conversation, events::add));

    assertEquals(4, reportedTokens(events));
  }

  @Test
  void shouldReportTokensOfAllStepsWhenDecisionAfterToolResultStaysInvalid() {
    AgentLlmGateway llmGateway =
        scriptedGateway(
            "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}",
            "not json",
            "still not json");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is two plus two?"));
    Agent agent = agent(llmGateway, calculator());

    List<AgentEvent> events = new ArrayList<>();

    assertThrows(AgentDecisionException.class, () -> agent.execute(conversation, events::add));

    assertEquals(6, reportedTokens(events));
  }

  @Test
  void shouldFailWithDecisionErrorWhenModelCallsToolAgainAfterToolResult() {
    String toolCall = "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}";
    AgentLlmGateway llmGateway = scriptedGateway(toolCall, toolCall);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is two plus two?"));
    Agent agent = agent(llmGateway, calculator());

    List<AgentEvent> events = new ArrayList<>();

    AgentDecisionException exception =
        assertThrows(AgentDecisionException.class, () -> agent.execute(conversation, events::add));
    assertEquals("Expected a model reply after tool execution", exception.getMessage());
    assertEquals(4, reportedTokens(events));
  }

  @Test
  void shouldReportUsageOfEveryLlmCallThatAddsUpToTheResult() {
    AgentLlmGateway llmGateway =
        scriptedGateway(
            "{\"type\":\"tool_call\",\"toolName\":\"calculator\",\"input\":\"2 + 2\"}",
            "{\"type\":\"model_reply\",\"content\":\"It is 4.\"}");
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is two plus two?"));
    List<AgentEvent> events = new ArrayList<>();

    AgentResult result = agent(llmGateway, calculator()).execute(conversation, events::add);

    assertEquals(2, events.stream().filter(TokenUsageEvent.class::isInstance).count());
    assertEquals(result.tokenUsage().totalTokens(), reportedTokens(events));
  }

  @Test
  void shouldReportUsageOfEmptyResponsesBeforeFailing() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    when(llmGateway.stream(any(), any(), any()))
        .thenReturn(new StreamingResult("test-model", new TokenUsage(552, 300)));
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("What is Spring?"));
    Agent agent = agent(llmGateway);
    List<AgentEvent> events = new ArrayList<>();

    assertThrows(LlmCommunicationException.class, () -> agent.execute(conversation, events::add));

    assertEquals(2 * (552 + 300), reportedTokens(events));
  }

  private static int reportedTokens(List<AgentEvent> events) {
    return events.stream()
        .filter(TokenUsageEvent.class::isInstance)
        .map(TokenUsageEvent.class::cast)
        .mapToInt(event -> event.usage().totalTokens())
        .sum();
  }

  @Test
  void shouldRejectDuplicateToolNames() {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter formatter = new ToolDescriptionFormatter();
    List<Tool> duplicateTools = List.of(calculator(), calculator());
    ObjectMapper objectMapper = new ObjectMapper();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolCallingAgent(llmGateway, formatter, duplicateTools, objectMapper));
    assertEquals("Duplicate tool name: calculator", exception.getMessage());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void shouldRejectToolWithoutName(String invalidName) {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    ToolDescriptionFormatter formatter = new ToolDescriptionFormatter();
    ObjectMapper objectMapper = new ObjectMapper();
    Tool unnamed = mock(Tool.class);
    when(unnamed.name()).thenReturn(invalidName);
    List<Tool> tools = List.of(unnamed);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolCallingAgent(llmGateway, formatter, tools, objectMapper));
    assertEquals("Tool name must not be blank", exception.getMessage());
  }

  private static Agent agent(AgentLlmGateway llmGateway, Tool... tools) {
    return new ToolCallingAgent(
        llmGateway, new ToolDescriptionFormatter(), List.of(tools), new ObjectMapper());
  }

  private static Tool calculator() {
    Tool calculator = mock(Tool.class);
    when(calculator.name()).thenReturn("calculator");
    when(calculator.description()).thenReturn("Calculates expressions.");
    when(calculator.resolveInput(any())).thenReturn(Optional.empty());
    when(calculator.execute(any())).thenReturn(ToolResult.success("2 + 2 = 4"));
    return calculator;
  }

  private static AgentLlmGateway scriptedGateway(String... responses) {
    return scriptedGateway(new ArrayList<>(), responses);
  }

  /** Answers each stream call with the next response and records the conversation it received. */
  private static AgentLlmGateway scriptedGateway(List<Conversation> requests, String... responses) {
    AgentLlmGateway llmGateway = mock(AgentLlmGateway.class);
    int[] call = {0};
    doAnswer(
        invocation -> {
          requests.add(invocation.getArgument(0));
          java.util.function.Consumer<ChatChunk> consumer = invocation.getArgument(2);
          consumer.accept(new ChatChunk(responses[call[0]++], ChatChunkType.CONTENT, true));
          return new StreamingResult("test-model", new TokenUsage(1, 1));
        })
        .when(llmGateway)
        .stream(any(), any(), any());
    return llmGateway;
  }
}
