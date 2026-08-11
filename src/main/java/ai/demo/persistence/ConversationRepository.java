package ai.demo.persistence;

import ai.demo.model.chat.Conversation;

public interface ConversationRepository {

  void save(Conversation conversation);

  Conversation load();
}
