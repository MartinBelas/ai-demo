package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.exception.AgentDecisionException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class ToolCallingAgent implements Agent {

  private final AgentLlmGateway llmGateway;
  private final ToolDescriptionFormatter toolDescriptionFormatter;
  private final List<Tool> tools;
  private final AgentDecisionParser decisionParser;

  public ToolCallingAgent(
      AgentLlmGateway llmGateway,
      ToolDescriptionFormatter toolDescriptionFormatter,
      List<Tool> tools,
      ObjectMapper objectMapper) {

    this.llmGateway = Objects.requireNonNull(llmGateway);
    this.toolDescriptionFormatter = Objects.requireNonNull(toolDescriptionFormatter);
    this.tools = List.copyOf(tools);
    this.decisionParser = new AgentDecisionParser(objectMapper);
  }

  @Override
  public AgentResult execute(Conversation conversation) {
    return execute(conversation, event -> {});
  }

  @Override
  public AgentResult execute(Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    Objects.requireNonNull(conversation);
    Objects.requireNonNull(eventConsumer);

    ResolvedToolCall resolvedToolCall = resolveToolCall(conversation);
    if (resolvedToolCall != null) {
      return executeResolvedToolCall(resolvedToolCall, eventConsumer);
    }

    AgentStep firstStep = requestDecision(conversation, eventConsumer);

    if (firstStep.decision() instanceof ModelReply(String content)) {

      eventConsumer.accept(new ContentEvent(content));

      return new AgentResult(
          content, firstStep.response().model(), firstStep.response().tokenUsage());
    }

    if (firstStep.decision() instanceof ToolCallDecision toolCall) {

      return executeToolCall(toolCall, conversation, firstStep.response(), eventConsumer);
    }

    throw new IllegalStateException(
        "Unsupported agent decision: " + firstStep.decision().getClass().getSimpleName());
  }

  private ResolvedToolCall resolveToolCall(Conversation conversation) {
    String request = conversation.messages().getLast().content();
    for (Tool tool : tools) {
      Optional<String> input = tool.resolveInput(request);
      if (input.isPresent()) {
        return new ResolvedToolCall(tool, input.orElseThrow());
      }
    }
    return null;
  }

  private AgentResult executeResolvedToolCall(
      ResolvedToolCall resolvedToolCall, Consumer<AgentEvent> eventConsumer) {
    Tool tool = resolvedToolCall.tool();
    String input = resolvedToolCall.input();
    eventConsumer.accept(new ToolCallEvent(tool.name(), input));
    ToolResult result = tool.execute(input);
    eventConsumer.accept(new ToolResultEvent(tool.name(), result.content()));
    eventConsumer.accept(new ContentEvent(result.content()));
    return new AgentResult(result.content(), tool.name(), new TokenUsage(0, 0));
  }

  private AgentResult executeToolCall(
      ToolCallDecision toolCall,
      Conversation conversation,
      LlmResponse firstResponse,
      Consumer<AgentEvent> eventConsumer) {

    eventConsumer.accept(new ToolCallEvent(toolCall.toolName(), toolCall.input()));

    Tool tool = findTool(toolCall.toolName());

    ToolResult toolResult = tool.execute(toolCall.input());

    eventConsumer.accept(new ToolResultEvent(tool.name(), toolResult.content()));

    if (tool.resultIsFinal()) {
      eventConsumer.accept(new ContentEvent(toolResult.content()));
      return new AgentResult(
          toolResult.content(), firstResponse.model(), firstResponse.tokenUsage());
    }

    conversation.add(ChatMessage.assistant(firstResponse.text()));
    conversation.add(ChatMessage.tool(tool.name(), toolResult.content()));

    AgentStep finalStep = requestDecision(conversation, eventConsumer);

    if (!(finalStep.decision() instanceof ModelReply(String content))) {

      throw new IllegalStateException("Expected model reply after tool execution");
    }

    eventConsumer.accept(new ContentEvent(content));

    TokenUsage totalUsage =
        addTokenUsage(firstResponse.tokenUsage(), finalStep.response().tokenUsage());

    return new AgentResult(content, finalStep.response().model(), totalUsage);
  }

  private AgentStep requestDecision(Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    LlmResponse response = requestResponse(conversation, eventConsumer);

    try {
      return new AgentStep(decisionParser.parse(response.text()), response);
    } catch (AgentDecisionException e) {
      Conversation repairConversation = createRepairConversation(conversation, response.text(), e);
      LlmResponse repairedResponse = requestResponse(repairConversation, eventConsumer);
      AgentDecision repairedDecision = decisionParser.parse(repairedResponse.text());
      TokenUsage totalUsage = addTokenUsage(response.tokenUsage(), repairedResponse.tokenUsage());
      LlmResponse combinedResponse =
          new LlmResponse(repairedResponse.text(), repairedResponse.model(), totalUsage);

      return new AgentStep(repairedDecision, combinedResponse);
    }
  }

  private LlmResponse requestResponse(
      Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    RawAgentResponse firstResponse = requestRawResponse(conversation, eventConsumer);

    if (!firstResponse.text().isBlank()) {
      return firstResponse.toLlmResponse();
    }

    Conversation retryConversation = createEmptyResponseRetryConversation(conversation);
    RawAgentResponse retryResponse = requestRawResponse(retryConversation, eventConsumer);

    if (retryResponse.text().isBlank()) {
      throw new LlmCommunicationException(
          "The model returned no response content. It may have exhausted the output token limit"
              + " while reasoning.");
    }

    return new LlmResponse(
        retryResponse.text(),
        retryResponse.model(),
        addTokenUsage(firstResponse.tokenUsage(), retryResponse.tokenUsage()));
  }

  private RawAgentResponse requestRawResponse(
      Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    String toolsDescription = toolDescriptionFormatter.format(tools);

    Map<String, String> variables = Map.of("tools", toolsDescription);

    StringBuilder responseContent = new StringBuilder();

    StreamingResult streamingResult =
        llmGateway.stream(
            conversation, variables, chunk -> handleChunk(chunk, responseContent, eventConsumer));

    return new RawAgentResponse(
        responseContent.toString(), streamingResult.model(), streamingResult.tokenUsage());
  }

  private Conversation createEmptyResponseRetryConversation(Conversation conversation) {
    Conversation retryConversation = new Conversation(conversation.messages());
    retryConversation.add(
        ChatMessage.user(
            "Retry the decision for the preceding user request. Return the required decision JSON"
                + " immediately. Do not include reasoning or any other text."));
    return retryConversation;
  }

  private Conversation createRepairConversation(
      Conversation conversation, String invalidResponse, AgentDecisionException error) {
    Conversation repairConversation = new Conversation(conversation.messages());
    repairConversation.add(
        ChatMessage.user(
            """
            Your previous response was invalid.
            Error: %s
            Response: %s
            Return only corrected JSON matching the required format.
            """
                .formatted(error.getMessage(), invalidResponse)));

    return repairConversation;
  }

  private void handleChunk(
      ChatChunk chunk, StringBuilder responseContent, Consumer<AgentEvent> eventConsumer) {

    if (chunk.type() == ChatChunkType.THINKING) {

      eventConsumer.accept(new ThinkingEvent(chunk.content()));

      return;
    }

    if (chunk.type() == ChatChunkType.CONTENT) {
      responseContent.append(chunk.content());
    }
  }

  private TokenUsage addTokenUsage(TokenUsage first, TokenUsage second) {

    return new TokenUsage(
        first.promptTokens() + second.promptTokens(),
        first.completionTokens() + second.completionTokens());
  }

  private Tool findTool(String toolName) {

    return tools.stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown tool: " + toolName));
  }

  private record RawAgentResponse(String text, String model, TokenUsage tokenUsage) {

    private LlmResponse toLlmResponse() {
      return new LlmResponse(text, model, tokenUsage);
    }
  }

  private record ResolvedToolCall(Tool tool, String input) {}
}
