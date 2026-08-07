package ai.demo.console;

import ai.demo.model.chat.Conversation;

public class ConsoleContext {

  private Conversation conversation;

  public ConsoleContext(Conversation conversation) {
    this.conversation = conversation;
  }

  public Conversation conversation() {
    return conversation;
  }

  public void setConversation(Conversation conversation) {
    this.conversation = conversation;
  }
}
