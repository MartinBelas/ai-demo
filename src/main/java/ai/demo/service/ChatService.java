package ai.demo.service;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentEvent;
import ai.demo.agent.AgentResult;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import java.util.Objects;
import java.util.function.Consumer;

public class ChatService {

  private final Agent agent;

  public ChatService(Agent agent) {

    this.agent = Objects.requireNonNull(agent);
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

    return new ChatResponse(result.answer(), result.model(), result.tokenUsage(), durationMs);
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
