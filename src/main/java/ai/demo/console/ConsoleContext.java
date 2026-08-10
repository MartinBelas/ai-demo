package ai.demo.console;

import ai.demo.console.command.ThinkingMode;
import ai.demo.model.chat.Conversation;

public class ConsoleContext {

  private Conversation conversation;
  private ThinkingMode thinkingMode = ThinkingMode.MINIMAL;

  public ConsoleContext(Conversation conversation) {
    this.conversation = conversation;
  }

  public Conversation conversation() {
    return conversation;
  }

  public void setConversation(Conversation conversation) {
    this.conversation = conversation;
  }

  public ThinkingMode thinkingMode() {
    return thinkingMode;
  }

  public void setThinkingMode(ThinkingMode mode) {
    this.thinkingMode = mode;
  }
}
