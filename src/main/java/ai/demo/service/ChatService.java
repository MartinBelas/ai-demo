package ai.demo.service;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;

public class ChatService {

  private final LlmClient llmClient;

  public ChatService(LlmClient llmClient) {
    this.llmClient = llmClient;
  }

  public ChatResponse ask(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (conversation.isEmpty()) {
      throw new IllegalStateException("conversation must not be empty");
    }

    long start = System.currentTimeMillis();

    LlmResponse llmResponse = llmClient.chat(conversation);

    long duration = System.currentTimeMillis() - start;

    return new ChatResponse(llmResponse.text(), llmResponse.model(), duration);
  }
}
