package ai.demo.service;

import ai.demo.client.LlmClient;
import ai.demo.model.chat.ChatRequest;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.ai.LlmRequest;
import ai.demo.model.ai.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for handling chat interactions with the LLM.
 * Measures request duration and delegates to the LLM client.
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
   * @param chatRequest the chat request containing the question
   * @return the chat response with the answer and timing information
   */
  public ChatResponse ask(ChatRequest chatRequest) {

    final long start = System.currentTimeMillis();

    final LlmRequest request = new LlmRequest(chatRequest.question());

    final LlmResponse response = llmClient.generate(request);

    final long end = System.currentTimeMillis();

    final long duration = end - start;

    log.info("LLM call took {} ms", duration);

    return new ChatResponse(response.text(), response.model(), duration);
  }
}
