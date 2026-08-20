package ai.demo.agent;

import ai.demo.model.chat.Conversation;
import java.util.function.Consumer;

public interface Agent {

  AgentResult execute(Conversation conversation);

  AgentResult execute(Conversation conversation, Consumer<AgentEvent> eventConsumer);
}
