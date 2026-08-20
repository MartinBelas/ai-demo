package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmResponse;
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

    AgentDecision decision = requestDecision(conversation);

    if (decision instanceof ModelReply(String content)) {
      return new AgentResult(content);
    }

    if (decision instanceof ToolCallDecision toolCall) {
      return executeToolCall(toolCall, conversation);
    }

    throw new IllegalStateException(
        "Unsupported agent decision: " + decision.getClass().getSimpleName());
  }

  private AgentResult executeToolCall(ToolCallDecision toolCall, Conversation conversation) {

    Tool tool = findTool(toolCall.toolName());

    ToolResult toolResult = tool.execute(toolCall.input());

    conversation.add(ChatMessage.tool(tool.name(), toolResult.content()));

    AgentDecision decision = requestDecision(conversation);

    if (decision instanceof ModelReply(String content)) {
      return new AgentResult(content);
    }

    throw new IllegalStateException("Expected model reply after tool execution");
  }

  private AgentDecision requestDecision(Conversation conversation) {

    String toolsDescription = toolDescriptionFormatter.format(tools);

    LlmResponse response = llmGateway.request(conversation, Map.of("tools", toolsDescription));

    return parseDecision(response.text());
  }

  private AgentDecision parseDecision(String response) {

    try {
      JsonNode root = objectMapper.readTree(response);

      String type = root.path("type").asText();

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
