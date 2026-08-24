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
  private final Map<String, String> defaultVariables;

  public PromptComposer(SystemPromptProvider systemPromptProvider) {
    this(systemPromptProvider, Map.of());
  }

  public PromptComposer(
      SystemPromptProvider systemPromptProvider, Map<String, String> defaultVariables) {
    this.systemPromptProvider = systemPromptProvider;
    this.defaultVariables = Map.copyOf(defaultVariables);
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

    Map<String, String> mergedVariables = new java.util.HashMap<>(defaultVariables);
    mergedVariables.putAll(variables);
    String systemPrompt = systemPromptProvider.getSystemPrompt(mergedVariables);

    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(ChatMessage.system(systemPrompt));
    }

    messages.addAll(conversation.messages());

    return new Prompt(messages);
  }
}
