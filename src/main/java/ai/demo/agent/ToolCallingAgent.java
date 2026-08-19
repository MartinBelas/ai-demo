package ai.demo.agent;

import ai.demo.agent.tool.Tool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.agent.tool.ToolResult;
import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ToolCallingAgent implements Agent {

  private final LlmClient llmClient;
  private final PromptComposer promptComposer;
  private final ToolDescriptionFormatter toolDescriptionFormatter;
  private final List<Tool> tools;
  private final ObjectMapper objectMapper;

  public ToolCallingAgent(
      LlmClient llmClient,
      PromptComposer promptComposer,
      ToolDescriptionFormatter toolDescriptionFormatter,
      List<Tool> tools,
      ObjectMapper objectMapper) {

    this.llmClient = Objects.requireNonNull(llmClient);
    this.promptComposer = Objects.requireNonNull(promptComposer);
    this.toolDescriptionFormatter = Objects.requireNonNull(toolDescriptionFormatter);
    this.tools = List.copyOf(tools);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Override
  public AgentResult execute(Conversation conversation) {

    AgentDecision decision = requestDecision(conversation);

    if (decision.tool() == null) {
      return new AgentResult(decision.answer());
    }

    Tool tool = findTool(decision.tool());

    ToolResult toolResult = tool.execute(decision.input());

    conversation.add(ChatMessage.tool(tool.name(), toolResult.content()));

    AgentDecision finalDecision = requestDecision(conversation);

    return new AgentResult(finalDecision.answer());
  }

  private AgentDecision requestDecision(Conversation conversation) {

    String toolsDescription = toolDescriptionFormatter.format(tools);

    Prompt prompt = promptComposer.compose(conversation, Map.of("tools", toolsDescription));

    LlmResponse response = llmClient.chat(prompt);

    return parseDecision(response.text());
  }

  private AgentDecision parseDecision(String response) {
    try {
      return objectMapper.readValue(response, AgentDecision.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse agent decision", e);
    }
  }

  private Tool findTool(String toolName) {
    return tools.stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown tool: " + toolName));
  }
}
