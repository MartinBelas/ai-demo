package ai.demo.service;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import java.util.function.Consumer;

public class ChatService {

  private final LlmClient llmClient;
  private final PromptComposer promptComposer;

  public ChatService(LlmClient llmClient, PromptComposer promptComposer) {
    this.llmClient = llmClient;
    this.promptComposer = promptComposer;
  }

  public ChatResponse ask(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (conversation.isEmpty()) {
      throw new IllegalStateException("conversation must not be empty");
    }

    long start = System.currentTimeMillis();

    Prompt prompt = promptComposer.compose(conversation);
    LlmResponse llmResponse = llmClient.chat(prompt);

    long duration = System.currentTimeMillis() - start;

    return new ChatResponse(llmResponse.text(), llmResponse.model(), duration);
  }

  public StreamingResult askStreaming(Conversation conversation, Consumer<ChatChunk> consumer) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (conversation.isEmpty()) {
      throw new IllegalStateException("conversation must not be empty");
    }

    if (consumer == null) {
      throw new IllegalArgumentException("consumer must not be null");
    }

    Prompt prompt = promptComposer.compose(conversation);

    return llmClient.stream(prompt, consumer);
  }
}
