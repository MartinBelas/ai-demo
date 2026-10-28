package ai.demo.service;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentEvent;
import ai.demo.agent.AgentResult;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.prompt.RagPromptComposer;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ChatService {

  private final Agent agent;
  private final RagService rag;
  private final RagPromptComposer ragPromptComposer;

  public ChatService(Agent agent) {
    this(agent, null, null);
  }

  /**
   * @param rag retrieval service, or {@code null} when RAG is disabled
   * @param ragPromptComposer required when {@code rag} is set
   */
  public ChatService(Agent agent, RagService rag, RagPromptComposer ragPromptComposer) {
    this.agent = Objects.requireNonNull(agent);
    if (rag != null) {
      Objects.requireNonNull(ragPromptComposer, "ragPromptComposer");
    }
    this.rag = rag;
    this.ragPromptComposer = ragPromptComposer;
  }

  public void validateRag(Conversation conversation) {
    validateConversation(conversation);
    if (rag == null) throw new ai.demo.exception.RagDisabledException();
    rag.validateQuery(conversation.messages().getLast().content());
  }

  public ChatResponse askWithRag(Conversation conversation, Consumer<AgentEvent> eventConsumer) {
    validateRag(conversation);
    long started = System.currentTimeMillis();
    var sources = rag.retrieve(conversation.messages().getLast().content());
    Conversation enriched = ragPromptComposer.compose(conversation, sources);
    ChatResponse response = ask(enriched, eventConsumer);
    return new ChatResponse(
        response.answer(),
        response.model(),
        response.tokenUsage(),
        System.currentTimeMillis() - started,
        sources);
  }

  public ChatResponse ask(Conversation conversation) {

    return ask(conversation, event -> {});
  }

  public ChatResponse ask(Conversation conversation, Consumer<AgentEvent> eventConsumer) {

    validateConversation(conversation);

    if (eventConsumer == null) {
      throw new IllegalArgumentException("eventConsumer must not be null");
    }

    long start = System.currentTimeMillis();

    AgentResult result = agent.execute(conversation, eventConsumer);

    long durationMs = System.currentTimeMillis() - start;

    return new ChatResponse(
        result.answer(),
        result.model(),
        result.tokenUsage(),
        durationMs,
        List.of(),
        result.webSources());
  }

  private void validateConversation(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (conversation.isEmpty()) {
      throw new IllegalStateException("conversation must not be empty");
    }
  }
}
