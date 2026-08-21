package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.exception.AgentDecisionException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private AgentResult executeToolCall(
      ToolCallDecision toolCall,
      Conversation conversation,
      LlmResponse firstResponse,
      Consumer<AgentEvent> eventConsumer) {

    eventConsumer.accept(new ToolCallEvent(toolCall.toolName(), toolCall.input()));

    Tool tool = findTool(toolCall.toolName());

    ToolResult toolResult = tool.execute(toolCall.input());

    eventConsumer.accept(new ToolResultEvent(tool.name(), toolResult.content()));

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

    String toolsDescription = toolDescriptionFormatter.format(tools);

    Map<String, String> variables = Map.of("tools", toolsDescription);

    StringBuilder responseContent = new StringBuilder();

    StreamingResult streamingResult =
        llmGateway.stream(
            conversation, variables, chunk -> handleChunk(chunk, responseContent, eventConsumer));

    String responseText = responseContent.toString();

    return new LlmResponse(responseText, streamingResult.model(), streamingResult.tokenUsage());
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
}
