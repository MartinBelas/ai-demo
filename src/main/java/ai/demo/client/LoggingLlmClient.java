package ai.demo.client;

import ai.demo.model.chat.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

  private final LlmClient delegate;

  public LoggingLlmClient(LlmClient delegate) {
    this.delegate = delegate;
  }

  @Override
  public LlmResponse chat(Conversation conversation) {

    log.info("Sending chat request (messages={})", conversation.messages().size());

    long start = System.nanoTime();

    try {

      LlmResponse response = delegate.chat(conversation);

      log.info(
          "Received response from model '{}' in {} ms", response.model(), elapsedMillis(start));

      return response;

    } catch (LlmClientException e) {

      log.error("LLM request failed after {} ms", elapsedMillis(start), e);

      throw e;
    }
  }

  private static long elapsedMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1_000_000;
  }
}
