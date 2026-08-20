package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class ToolCallingAgent implements Agent {

  private final AgentLlmGateway llmGateway;
  private final ToolDescriptionFormatter toolDescriptionFormatter;
  private final List<Tool> tools;
  private final ObjectMapper objectMapper;

  public ToolCallingAgent(
      AgentLlmGateway llmGateway,
      ToolDescriptionFormatter toolDescriptionFormatter,
      List<Tool> tools,
      ObjectMapper objectMapper) {

    this.llmGateway = Objects.requireNonNull(llmGateway);
    this.toolDescriptionFormatter = Objects.requireNonNull(toolDescriptionFormatter);
    this.tools = List.copyOf(tools);
    this.objectMapper = Objects.requireNonNull(objectMapper);
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

    String toolsDescription = toolDescriptionFormatter.format(tools);

    Map<String, String> variables = Map.of("tools", toolsDescription);

    StringBuilder responseContent = new StringBuilder();

    StreamingResult streamingResult =
        llmGateway.stream(
            conversation, variables, chunk -> handleChunk(chunk, responseContent, eventConsumer));

    String responseText = responseContent.toString();

    AgentDecision decision = parseDecision(responseText);

    LlmResponse response =
        new LlmResponse(responseText, streamingResult.model(), streamingResult.tokenUsage());

    return new AgentStep(decision, response);
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

  private AgentDecision parseDecision(String response) {

    try {
      JsonNode root = objectMapper.readTree(response);

      String type = requiredText(root, "type");

      return switch (type) {
        case "tool_call" ->
            new ToolCallDecision(requiredText(root, "toolName"), requiredText(root, "input"));

        case "model_reply" -> new ModelReply(requiredText(root, "content"));

        default -> throw new IllegalStateException("Unknown agent decision type: " + type);
      };

    } catch (JsonProcessingException e) {

      throw new IllegalStateException("Failed to parse agent decision", e);
    }
  }

  private TokenUsage addTokenUsage(TokenUsage first, TokenUsage second) {

    return new TokenUsage(
        first.promptTokens() + second.promptTokens(),
        first.completionTokens() + second.completionTokens());
  }

  private String requiredText(JsonNode root, String fieldName) {

    JsonNode node = root.get(fieldName);

    if (node == null || node.isNull() || node.asText().isBlank()) {

      throw new IllegalStateException("Agent response is missing required field: " + fieldName);
    }

    return node.asText();
  }

  private Tool findTool(String toolName) {

    return tools.stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown tool: " + toolName));
  }
}
