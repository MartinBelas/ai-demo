package ai.demo.client;

import ai.demo.exception.LlmException;
import ai.demo.model.chat.Conversation;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

  private final LlmClient delegate;

  public LoggingLlmClient(LlmClient delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public LlmResponse chat(Conversation conversation) {

    String requestId = UUID.randomUUID().toString().substring(0, 8);

    log.info("[{}] Sending chat request (messages={})", requestId, conversation.messages().size());

    long start = System.nanoTime();

    try {
      LlmResponse response = delegate.chat(conversation);

      log.info(
          "[{}] Received response from model '{}' in {} ms",
          requestId,
          response.model(),
          elapsedMillis(start));

      return response;

    } catch (LlmException e) {

      log.error("[{}] LLM request failed after {} ms", requestId, elapsedMillis(start), e);

      throw e;

    } catch (RuntimeException e) {

      log.error("[{}] Unexpected error after {} ms", requestId, elapsedMillis(start), e);

      throw e;
    }
  }

  private static long elapsedMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1_000_000;
  }
}
