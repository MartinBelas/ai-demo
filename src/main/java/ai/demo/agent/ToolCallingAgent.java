package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    AgentStep firstStep = requestDecision(conversation);

    if (firstStep.decision() instanceof ModelReply(String content)) {
      return new AgentResult(
          content, firstStep.response().model(), firstStep.response().tokenUsage());
    }

    if (firstStep.decision() instanceof ToolCallDecision toolCall) {
      return executeToolCall(toolCall, conversation, firstStep.response());
    }

    throw new IllegalStateException(
        "Unsupported agent decision: " + firstStep.decision().getClass().getSimpleName());
  }

  private AgentResult executeToolCall(
      ToolCallDecision toolCall, Conversation conversation, LlmResponse firstResponse) {

    Tool tool = findTool(toolCall.toolName());

    ToolResult toolResult = tool.execute(toolCall.input());

    conversation.add(ChatMessage.tool(tool.name(), toolResult.content()));

    AgentStep finalStep = requestDecision(conversation);

    if (!(finalStep.decision() instanceof ModelReply(String content))) {
      throw new IllegalStateException("Expected model reply after tool execution");
    }

    TokenUsage totalUsage =
        addTokenUsage(firstResponse.tokenUsage(), finalStep.response().tokenUsage());

    return new AgentResult(content, finalStep.response().model(), totalUsage);
  }

  private AgentStep requestDecision(Conversation conversation) {

    String toolsDescription = toolDescriptionFormatter.format(tools);

    LlmResponse response = llmGateway.request(conversation, Map.of("tools", toolsDescription));

    AgentDecision decision = parseDecision(response.text());

    return new AgentStep(decision, response);
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
