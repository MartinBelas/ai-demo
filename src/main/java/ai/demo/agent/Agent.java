package ai.demo.agent;

import ai.demo.model.chat.Conversation;

public interface Agent {

  AgentResult execute(Conversation conversation);
}
