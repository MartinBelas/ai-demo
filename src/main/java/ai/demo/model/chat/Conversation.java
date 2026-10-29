package ai.demo.model.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public record Conversation(@JsonProperty("messages") List<ChatMessage> messages) {

  @JsonCreator
  public Conversation(@JsonProperty("messages") List<ChatMessage> messages) {
    this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
  }

  public Conversation() {
    this(new ArrayList<>());
  }

  public void add(ChatMessage message) {
    if (message.role() == Role.SYSTEM) {
      throw new IllegalArgumentException("Conversation must not contain system messages");
    }
    messages.add(message);
  }

  /** Returns a copy of this conversation with the message appended; this conversation is kept. */
  public Conversation withMessage(ChatMessage message) {
    Conversation copy = new Conversation(messages);
    copy.add(message);
    return copy;
  }

  @Override
  public List<ChatMessage> messages() {
    return List.copyOf(messages);
  }

  public boolean isEmpty() {
    return messages.isEmpty();
  }

  public int size() {
    return messages.size();
  }
}
