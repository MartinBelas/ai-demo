package ai.demo.model.prompt;

import ai.demo.model.chat.ChatMessage;
import java.util.List;

public record Prompt(List<ChatMessage> messages) {

  public Prompt {
    if (messages == null) {
      throw new IllegalArgumentException("messages must not be null");
    }
    if (messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }
  }
}
