package ai.demo.service;

import ai.demo.client.LlmClient;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.ai.LlmRequest;
import ai.demo.model.ai.LlmResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for handling chat interactions with the LLM. Measures request duration and delegates to
 * the LLM client.
 */
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private final LlmClient llmClient;

  /**
   * Creates a new ChatService.
   *
   * @param llmClient the LLM client to use for generating responses
   */
  public ChatService(LlmClient llmClient) {
    this.llmClient = llmClient;
  }

  /**
   * Asks the LLM a question and returns the response.
   *
   * @param conversation
   * @return the chat response with the answer and timing information
   */
  public ChatResponse ask(Conversation conversation) {

    final long start = System.currentTimeMillis();

    ChatMessage lastMessage = findLastUserMessage(conversation);

    LlmRequest request = new LlmRequest(lastMessage.content());

    final LlmResponse response = llmClient.generate(request);

    final long end = System.currentTimeMillis();

    final long duration = end - start;

    log.info("LLM call took {} ms", duration);

    return new ChatResponse(response.text(), response.model(), duration);
  }

  private ChatMessage findLastUserMessage(Conversation conversation) {

    List<ChatMessage> messages = conversation.messages();

    for (int i = messages.size() - 1; i >= 0; i--) {

      ChatMessage message = messages.get(i);

      if (message.role() == Role.USER) {
        return message;
      }
    }

    throw new IllegalStateException("No user message found");
  }
}
