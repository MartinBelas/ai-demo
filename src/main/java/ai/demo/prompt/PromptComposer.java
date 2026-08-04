package ai.demo.prompt;

import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;

public class PromptComposer {

  public Prompt compose(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    return new Prompt(conversation.messages());
  }
}
