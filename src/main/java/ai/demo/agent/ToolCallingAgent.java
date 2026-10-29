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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ToolCallingAgent implements Agent {

  private final AgentLlmGateway llmGateway;
  private final ToolDescriptionFormatter toolDescriptionFormatter;
  private final List<Tool> tools;
  private final Map<String, Tool> toolsByName;
  private final AgentDecisionParser decisionParser;

  public ToolCallingAgent(
      AgentLlmGateway llmGateway,
      ToolDescriptionFormatter toolDescriptionFormatter,
      List<Tool> tools,
      ObjectMapper objectMapper) {

    this.llmGateway = Objects.requireNonNull(llmGateway);
    this.toolDescriptionFormatter = Objects.requireNonNull(toolDescriptionFormatter);
    this.tools = List.copyOf(tools);
    this.toolsByName = indexByName(this.tools);
    this.decisionParser = new AgentDecisionParser(objectMapper);
  }

  /**
   * The prompt lists every tool by name, so a missing or repeated name would make the model's
   * choice ambiguous; reject it at construction time.
   */
  private static Map<String, Tool> indexByName(List<Tool> tools) {
    Map<String, Tool> byName = new HashMap<>();
    for (Tool tool : tools) {
      String name = tool.name();
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Tool name must not be blank");
      }
      if (byName.putIfAbsent(name, tool) != null) {
        throw new IllegalArgumentException("Duplicate tool name: " + name);
      }
    }
    return Map.copyOf(byName);
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

    // AgentDecision is sealed, so the compiler checks that every decision type is handled.
    return switch (firstStep.decision()) {
      case ModelReply(String content) -> {
        eventConsumer.accept(new ContentEvent(content));
        yield new AgentResult(
            content, firstStep.response().model(), firstStep.response().tokenUsage());
      }
      case ToolCallDecision toolCall ->
          executeToolCall(toolCall, conversation, firstStep.response(), eventConsumer);
    };
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

    Tool tool = requireTool(toolCall.toolName());

    ToolResult toolResult = tool.execute(toolCall.input());

    eventConsumer.accept(new ToolResultEvent(tool.name(), toolResult.content()));

    if (tool.resultIsFinal()) {
      eventConsumer.accept(new ContentEvent(toolResult.content()));
      return new AgentResult(
          toolResult.content(), firstResponse.model(), firstResponse.tokenUsage());
    }

    // The tool round is working state of this request: a copy keeps the raw decision JSON and the
    // tool message out of the caller's conversation (e.g. the persisted console history).
    Conversation toolConversation =
        conversation
            .withMessage(ChatMessage.assistant(firstResponse.text()))
            .withMessage(ChatMessage.tool(tool.name(), toolResult.content()));

    AgentStep finalStep = requestDecision(toolConversation, eventConsumer);

    if (!(finalStep.decision() instanceof ModelReply(String content))) {
      throw new AgentDecisionException("Expected a model reply after tool execution");
    }

    eventConsumer.accept(new ContentEvent(content));

    TokenUsage totalUsage =
        addTokenUsage(firstResponse.tokenUsage(), finalStep.response().tokenUsage());

    return new AgentResult(content, finalStep.response().model(), totalUsage);
  }

  private AgentStep requestDecision(Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    LlmResponse response = requestResponse(conversation, eventConsumer);

    try {
      return new AgentStep(parseDecision(response.text()), response);
    } catch (AgentDecisionException e) {
      return requestRepairedDecision(conversation, response, e, eventConsumer);
    }
  }

  private AgentStep requestRepairedDecision(
      Conversation conversation,
      LlmResponse invalidResponse,
      AgentDecisionException error,
      Consumer<AgentEvent> eventConsumer) {

    Conversation repairConversation =
        createRepairConversation(conversation, invalidResponse.text(), error);
    LlmResponse repairedResponse = requestResponse(repairConversation, eventConsumer);
    TokenUsage totalUsage =
        addTokenUsage(invalidResponse.tokenUsage(), repairedResponse.tokenUsage());

    AgentDecision repairedDecision = parseDecision(repairedResponse.text());

    LlmResponse combinedResponse =
        new LlmResponse(repairedResponse.text(), repairedResponse.model(), totalUsage);

    return new AgentStep(repairedDecision, combinedResponse);
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

    // Every LLM call goes through here, so this is the single place where usage is reported, before
    // the response is validated and possibly rejected.
    eventConsumer.accept(new TokenUsageEvent(streamingResult.tokenUsage()));

    return new RawAgentResponse(
        responseContent.toString(), streamingResult.model(), streamingResult.tokenUsage());
  }

  private Conversation createEmptyResponseRetryConversation(Conversation conversation) {
    return conversation.withMessage(
        ChatMessage.user(
            "Retry the decision for the preceding user request. Return the required decision JSON"
                + " immediately. Do not include reasoning or any other text."));
  }

  private Conversation createRepairConversation(
      Conversation conversation, String invalidResponse, AgentDecisionException error) {
    return conversation.withMessage(
        ChatMessage.user(
            """
            Your previous response was invalid.
            Error: %s
            Response: %s
            Return only corrected JSON matching the required format.
            """
                .formatted(error.getMessage(), invalidResponse)));
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

  /**
   * Parses the decision and rejects calls to tools that do not exist, so an invented tool name gets
   * the same single repair attempt as malformed JSON.
   */
  private AgentDecision parseDecision(String text) {
    AgentDecision decision = decisionParser.parse(text);
    if (decision instanceof ToolCallDecision toolCall) {
      requireTool(toolCall.toolName());
    }
    return decision;
  }

  private AgentDecisionException unknownTool(String toolName) {
    String available = tools.stream().map(Tool::name).collect(Collectors.joining(", "));
    return new AgentDecisionException(
        "Unknown tool: "
            + toolName
            + ". Available tools: "
            + (available.isEmpty() ? "none" : available));
  }

  private Tool requireTool(String toolName) {
    Tool tool = toolsByName.get(toolName);
    if (tool == null) {
      throw unknownTool(toolName);
    }
    return tool;
  }

  private record RawAgentResponse(String text, String model, TokenUsage tokenUsage) {

    private LlmResponse toLlmResponse() {
      return new LlmResponse(text, model, tokenUsage);
    }
  }

  private record ResolvedToolCall(Tool tool, String input) {}
}
