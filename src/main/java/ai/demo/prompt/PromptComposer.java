package ai.demo.prompt;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import java.util.ArrayList;
import java.util.List;

public class PromptComposer {

  private final String systemPrompt;

  public PromptComposer(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public Prompt compose(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    List<ChatMessage> messages = new ArrayList<>();

    if (!systemPrompt.isBlank()) {
      messages.add(ChatMessage.system(systemPrompt));
    }

    messages.addAll(conversation.messages());

    return new Prompt(messages);
  }
}
