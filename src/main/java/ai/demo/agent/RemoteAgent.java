package ai.demo.agent;

import ai.demo.client.TokenUsage;
import ai.demo.client.agentlab.AgentLabClient;
import ai.demo.client.agentlab.AgentLabMessage;
import ai.demo.client.agentlab.AgentLabRequest;
import ai.demo.client.agentlab.AgentLabResponse;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import ai.demo.model.chat.WebSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Agent mode: delegates the whole agent run (model calls and tools such as web search) to the
 * external agent service instead of the built-in {@link ToolCallingAgent}.
 *
 * <p>The agent service is stateless, so every request carries the latest user message plus earlier
 * turns as history. History is fitted to the service's contract limits: older turns are dropped and
 * over-long earlier messages are shortened. The latest message itself is never shortened; the API
 * rejects it when it exceeds {@link #MAX_MESSAGE_CHARACTERS}. The answer is emitted as a single
 * content event, because the first version of agent mode does not stream intermediate steps.
 */
public final class RemoteAgent implements Agent {

  /** Per-message limit of the agent service contract (v1). */
  public static final int MAX_MESSAGE_CHARACTERS = 4000;

  static final int MAX_HISTORY_MESSAGES = 20;
  static final int MAX_HISTORY_CHARACTERS = 16000;

  /** Reported as the model; the agent service chooses its own model and does not report it. */
  static final String MODEL = "agent-lab";

  private final AgentLabClient client;

  public RemoteAgent(AgentLabClient client) {
    this.client = Objects.requireNonNull(client);
  }

  @Override
  public AgentResult execute(Conversation conversation) {
    return execute(conversation, event -> {});
  }

  @Override
  public AgentResult execute(Conversation conversation, Consumer<AgentEvent> eventConsumer) {
    List<ChatMessage> messages = conversation.messages();
    ChatMessage latest = messages.getLast();
    if (latest.role() != Role.USER) {
      throw new IllegalArgumentException("The last message must be a user message");
    }
    if (latest.content().length() > MAX_MESSAGE_CHARACTERS) {
      throw new IllegalArgumentException("The user message is too long for agent mode");
    }

    AgentLabResponse response =
        client.chat(
            new AgentLabRequest(
                latest.content(), history(messages.subList(0, messages.size() - 1))));

    eventConsumer.accept(new ContentEvent(response.reply()));
    return new AgentResult(
        response.reply(),
        MODEL,
        new TokenUsage(response.usage().inputTokens(), response.usage().outputTokens()),
        response.sources().stream()
            .map(source -> new WebSource(source.title(), source.url()))
            .toList());
  }

  /** The newest earlier turns that fit the agent service's history limits, oldest first. */
  static List<AgentLabMessage> history(List<ChatMessage> earlier) {
    List<ChatMessage> turns =
        earlier.stream()
            .filter(message -> message.role() == Role.USER || message.role() == Role.ASSISTANT)
            .toList();
    List<AgentLabMessage> history = new ArrayList<>();
    int characters = 0;
    for (int index = turns.size() - 1;
        index >= 0 && history.size() < MAX_HISTORY_MESSAGES;
        index--) {
      ChatMessage message = turns.get(index);
      String content = shortened(message.content());
      if (characters + content.length() > MAX_HISTORY_CHARACTERS) {
        break;
      }
      characters += content.length();
      history.add(
          message.role() == Role.USER
              ? AgentLabMessage.user(content)
              : AgentLabMessage.assistant(content));
    }
    Collections.reverse(history);
    return history;
  }

  private static String shortened(String content) {
    if (content.length() <= MAX_MESSAGE_CHARACTERS) {
      return content;
    }
    int end = MAX_MESSAGE_CHARACTERS;
    if (Character.isHighSurrogate(content.charAt(end - 1))) {
      end--; // Do not split a surrogate pair.
    }
    return content.substring(0, end);
  }
}
