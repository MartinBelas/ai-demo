package ai.demo.agent;

import ai.demo.exception.AgentDecisionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public class AgentDecisionParser {

  private final ObjectMapper objectMapper;

  public AgentDecisionParser(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  public AgentDecision parse(String response) {
    try {
      JsonNode root = objectMapper.readTree(response);
      String type = requiredText(root, "type");

      return switch (type) {
        case "tool_call" ->
            new ToolCallDecision(requiredText(root, "toolName"), requiredText(root, "input"));
        case "model_reply" -> new ModelReply(requiredText(root, "content"));
        default -> throw new AgentDecisionException("Unknown agent decision type: " + type);
      };
    } catch (JsonProcessingException e) {
      throw new AgentDecisionException("Failed to parse agent decision", e);
    }
  }

  private String requiredText(JsonNode root, String fieldName) {
    JsonNode node = root != null ? root.get(fieldName) : null;

    if (node == null || node.isNull() || node.asText().isBlank()) {
      throw new AgentDecisionException("Agent response is missing required field: " + fieldName);
    }

    return node.asText();
  }
}
