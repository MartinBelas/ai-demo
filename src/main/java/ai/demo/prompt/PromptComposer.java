package ai.demo.prompt;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.template.SystemPromptProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PromptComposer {

  private final SystemPromptProvider systemPromptProvider;

  public PromptComposer(SystemPromptProvider systemPromptProvider) {
    this.systemPromptProvider = systemPromptProvider;
  }

  public Prompt compose(Conversation conversation) {
    return compose(conversation, Map.of());
  }

  public Prompt compose(Conversation conversation, Map<String, String> variables) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (variables == null) {
      throw new IllegalArgumentException("variables must not be null");
    }

    List<ChatMessage> messages = new ArrayList<>();

    String systemPrompt = systemPromptProvider.getSystemPrompt(variables);

    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(ChatMessage.system(systemPrompt));
    }

    messages.addAll(conversation.messages());

    return new Prompt(messages);
  }
}
