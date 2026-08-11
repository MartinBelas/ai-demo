package ai.demo.client;

import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.prompt.Prompt;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

  private final LlmClient delegate;

  public LoggingLlmClient(LlmClient delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public LlmResponse chat(Prompt prompt) {

    String requestId = UUID.randomUUID().toString().substring(0, 8);

    log.info("[{}] Sending chat request (messages={})", requestId, prompt.messages().size());

    long start = System.nanoTime();

    try {
      LlmResponse response = delegate.chat(prompt);

      log.info(
          "[{}] Received response from model '{}' in {} ms (promptTokens={}, completionTokens={}, totalTokens={})",
          requestId,
          response.model(),
          elapsedMillis(start),
          response.tokenUsage().promptTokens(),
          response.tokenUsage().completionTokens(),
          response.tokenUsage().totalTokens());

      return response;
    } catch (LlmException e) {
      log.error("[{}] LLM request failed after {} ms", requestId, elapsedMillis(start), e);
      throw e;
    } catch (RuntimeException e) {
      log.error("[{}] Unexpected error after {} ms", requestId, elapsedMillis(start), e);
      throw e;
    }
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {

    long start = System.nanoTime();
    long requestId = ThreadLocalRandom.current().nextLong();

    log.info("[{}] Sending streaming prompt (messages={})", requestId, prompt.messages().size());

    try {

      StreamingResult result = delegate.stream(prompt, consumer);

      log.info(
          "[{}] Token usage: promptTokens={}, completionTokens={}, totalTokens={}",
          requestId,
          result.tokenUsage().promptTokens(),
          result.tokenUsage().completionTokens(),
          result.tokenUsage().totalTokens());

      return result;
    } catch (LlmException e) {
      log.error("[{}] LLM streaming failed after {} ms", requestId, elapsedMillis(start), e);
      throw e;
    } catch (RuntimeException e) {
      log.error(
          "[{}] Unexpected error during LLM streaming after {} ms",
          requestId,
          elapsedMillis(start),
          e);
      throw e;
    }
  }

  private static long elapsedMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1_000_000;
  }
}
