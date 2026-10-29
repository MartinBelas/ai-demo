package ai.demo.client;

import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.prompt.Prompt;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs every LLM call with its duration and token usage. The request ID is not part of the
 * messages: it comes from the MDC ({@link ai.demo.logging.RequestId}), so all calls of one API
 * request, including the agent's tool rounds, share the ID of that request.
 */
public class LoggingLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

  private final LlmClient delegate;

  public LoggingLlmClient(LlmClient delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public LlmResponse chat(Prompt prompt) {

    log.info("Sending chat request (messages={})", prompt.messages().size());

    long start = System.nanoTime();

    try {
      LlmResponse response = delegate.chat(prompt);

      log.info(
          "Received response from model '{}' in {} ms (promptTokens={}, completionTokens={}, totalTokens={})",
          response.model(),
          elapsedMillis(start),
          response.tokenUsage().promptTokens(),
          response.tokenUsage().completionTokens(),
          response.tokenUsage().totalTokens());

      return response;
    } catch (RuntimeException e) {
      logFailure("request", start, e);
      throw e;
    }
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {

    long start = System.nanoTime();
    log.info("Sending streaming prompt (messages={})", prompt.messages().size());
    AtomicReference<RuntimeException> consumerFailure = new AtomicReference<>();

    try {
      StreamingResult result =
          delegate.stream(prompt, rememberingFailure(consumer, consumerFailure));

      log.info(
          "Token usage: promptTokens={}, completionTokens={}, totalTokens={}",
          result.tokenUsage().promptTokens(),
          result.tokenUsage().completionTokens(),
          result.tokenUsage().totalTokens());

      return result;
    } catch (RuntimeException e) {
      if (isCausedBy(e, consumerFailure.get())) {
        // Not an LLM failure: typically the client disconnected while the model was still
        // streaming, which the caller handles as a normal outcome.
        log.debug(
            "LLM streaming stopped by its consumer after {} ms: {}",
            elapsedMillis(start),
            e.toString());
      } else {
        logFailure("streaming", start, e);
      }
      throw e;
    }
  }

  /**
   * Records the duration and the type and message of a failed call, without the stack trace. The
   * exception is rethrown: the caller that handles it logs it with its stack trace, so the trace is
   * not logged twice, while the cause stays visible even if a caller swallows the exception.
   */
  private static void logFailure(String operation, long startNanoTime, RuntimeException e) {
    // toString() on purpose: a Throwable as the last argument would make SLF4J log its stack trace.
    String cause = e.toString();
    long elapsedMillis = elapsedMillis(startNanoTime);
    if (e instanceof LlmException) {
      log.warn("LLM {} failed after {} ms: {}", operation, elapsedMillis, cause);
    } else {
      log.error("Unexpected error during LLM {} after {} ms: {}", operation, elapsedMillis, cause);
    }
  }

  /** Remembers the consumer's failure, to tell it from a failure of the provider. */
  private static Consumer<ChatChunk> rememberingFailure(
      Consumer<ChatChunk> consumer, AtomicReference<RuntimeException> consumerFailure) {
    return chunk -> {
      try {
        consumer.accept(chunk);
      } catch (RuntimeException e) {
        consumerFailure.set(e);
        throw e;
      }
    };
  }

  /** True if {@code failure} is {@code cause} or was caused by it, e.g. wrapped by a provider. */
  private static boolean isCausedBy(Throwable failure, Throwable cause) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current == cause) {
        return true;
      }
    }
    return false;
  }

  private static long elapsedMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1_000_000;
  }
}
