package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.prompt.Prompt;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingLlmClientTest {

  private static final Prompt PROMPT = new Prompt(List.of(ChatMessage.user("Test question")));
  private static final StreamingResult STREAMING_RESULT =
      new StreamingResult("test-model", new TokenUsage(0, 0));

  private final LlmClient delegate = mock(LlmClient.class);
  private final LoggingLlmClient loggingClient = new LoggingLlmClient(delegate);

  @Test
  void shouldDelegateAndReturnSuccessfulResponse() {
    TokenUsage usage = new TokenUsage(0, 0);
    LlmResponse response = new LlmResponse("Test response", "test-model", usage);
    when(delegate.chat(any(Prompt.class))).thenReturn(response);

    LlmResponse result = loggingClient.chat(PROMPT);

    assertEquals("Test response", result.text());
    assertEquals("test-model", result.model());
    assertEquals(usage, result.tokenUsage());
  }

  @Test
  void shouldDelegateStreamingResponseAndPassChunksToTheConsumer() {
    ChatChunk chunk = new ChatChunk("Hel", ChatChunkType.CONTENT, false);
    when(delegate.stream(any(Prompt.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ChatChunk> consumer = invocation.getArgument(1);
              consumer.accept(chunk);
              return STREAMING_RESULT;
            });
    @SuppressWarnings("unchecked")
    Consumer<ChatChunk> consumer = mock(Consumer.class);

    StreamingResult result = loggingClient.stream(PROMPT, consumer);

    verify(delegate).stream(eq(PROMPT), any());
    verify(consumer).accept(chunk);
    assertEquals(0, result.tokenUsage().promptTokens());
    assertEquals(0, result.tokenUsage().completionTokens());
  }

  @Test
  void shouldRethrowAndLogFailedChatAsWarningWithCauseButWithoutStackTrace() {
    LlmCommunicationException exception = new LlmCommunicationException("Connection failed");
    when(delegate.chat(any(Prompt.class))).thenThrow(exception);

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(
                        LlmCommunicationException.class, () -> loggingClient.chat(PROMPT))));

    assertSingleFailureLog(events, Level.WARN, "LLM request failed after", "Connection failed");
  }

  @Test
  void shouldRethrowAndLogFailedStreamAsWarningWithCauseButWithoutStackTrace() {
    LlmCommunicationException exception = new LlmCommunicationException("Streaming failed");
    when(delegate.stream(any(Prompt.class), any())).thenThrow(exception);

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(
                        LlmCommunicationException.class,
                        () -> loggingClient.stream(PROMPT, chunk -> {}))));

    assertSingleFailureLog(events, Level.WARN, "LLM streaming failed after", "Streaming failed");
  }

  @Test
  void shouldRethrowAndLogUnexpectedChatErrorAsError() {
    RuntimeException exception = new IllegalStateException("Broken");
    when(delegate.chat(any(Prompt.class))).thenThrow(exception);

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(IllegalStateException.class, () -> loggingClient.chat(PROMPT))));

    assertSingleFailureLog(
        events, Level.ERROR, "Unexpected error during LLM request after", "Broken");
  }

  @Test
  void shouldRethrowAndLogUnexpectedStreamErrorAsError() {
    RuntimeException exception = new IllegalStateException("Broken");
    when(delegate.stream(any(Prompt.class), any())).thenThrow(exception);

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(
                        IllegalStateException.class,
                        () -> loggingClient.stream(PROMPT, chunk -> {}))));

    assertSingleFailureLog(
        events, Level.ERROR, "Unexpected error during LLM streaming after", "Broken");
  }

  /**
   * The consumer failing is not an LLM failure: typically the client disconnected while the model
   * was still thinking, which the caller already treats as a normal outcome.
   */
  @Test
  void shouldRethrowConsumerFailureWithoutLoggingItAsLlmFailure() {
    RuntimeException exception = new IllegalStateException("Client disconnected");
    when(delegate.stream(any(Prompt.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ChatChunk> consumer = invocation.getArgument(1);
              consumer.accept(new ChatChunk("Hel", ChatChunkType.CONTENT, false));
              return STREAMING_RESULT;
            });
    Consumer<ChatChunk> failingConsumer =
        chunk -> {
          throw exception;
        };

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(
                        IllegalStateException.class,
                        () -> loggingClient.stream(PROMPT, failingConsumer))));

    assertConsumerStopLoggedAtDebugOnly(events, "Client disconnected");
  }

  @Test
  void shouldTreatProviderFailureCausedByTheConsumerAsConsumerFailure() {
    RuntimeException consumerFailure = new IllegalStateException("Client disconnected");
    LlmCommunicationException wrapped =
        new LlmCommunicationException("Streaming failed", consumerFailure);
    when(delegate.stream(any(Prompt.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ChatChunk> consumer = invocation.getArgument(1);
              try {
                consumer.accept(new ChatChunk("Hel", ChatChunkType.CONTENT, false));
              } catch (RuntimeException e) {
                throw wrapped;
              }
              return STREAMING_RESULT;
            });
    Consumer<ChatChunk> failingConsumer =
        chunk -> {
          throw consumerFailure;
        };

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    wrapped,
                    assertThrows(
                        LlmCommunicationException.class,
                        () -> loggingClient.stream(PROMPT, failingConsumer))));

    assertConsumerStopLoggedAtDebugOnly(events, "Streaming failed");
  }

  @Test
  void shouldStillLogProviderFailureUnrelatedToEarlierConsumerFailure() {
    LlmCommunicationException providerFailure = new LlmCommunicationException("Stream broke");
    when(delegate.stream(any(Prompt.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ChatChunk> consumer = invocation.getArgument(1);
              try {
                consumer.accept(new ChatChunk("Hel", ChatChunkType.CONTENT, false));
              } catch (RuntimeException ignored) {
                // The provider swallows the consumer's failure and then fails for its own reason.
              }
              throw providerFailure;
            });
    Consumer<ChatChunk> failingConsumer =
        chunk -> {
          throw new IllegalStateException("Client disconnected");
        };

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    providerFailure,
                    assertThrows(
                        LlmCommunicationException.class,
                        () -> loggingClient.stream(PROMPT, failingConsumer))));

    assertSingleFailureLog(events, Level.WARN, "LLM streaming failed after", "Stream broke");
  }

  @Test
  void shouldStillLogProviderFailureAfterConsumerReceivedChunks() {
    LlmCommunicationException exception = new LlmCommunicationException("Stream broke");
    when(delegate.stream(any(Prompt.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ChatChunk> consumer = invocation.getArgument(1);
              consumer.accept(new ChatChunk("Hel", ChatChunkType.CONTENT, false));
              throw exception;
            });

    List<ILoggingEvent> events =
        capturedLogs(
            () ->
                assertSame(
                    exception,
                    assertThrows(
                        LlmCommunicationException.class,
                        () -> loggingClient.stream(PROMPT, chunk -> {}))));

    assertSingleFailureLog(events, Level.WARN, "LLM streaming failed after", "Stream broke");
  }

  @Test
  void shouldNotLogFailureForSuccessfulChat() {
    when(delegate.chat(any(Prompt.class)))
        .thenReturn(new LlmResponse("Test response", "test-model", new TokenUsage(0, 0)));

    List<ILoggingEvent> events = capturedLogs(() -> loggingClient.chat(PROMPT));

    assertTrue(warningsAndErrors(events).isEmpty(), events.toString());
  }

  @Test
  void shouldNotLogFailureForSuccessfulStream() {
    when(delegate.stream(any(Prompt.class), any())).thenReturn(STREAMING_RESULT);

    List<ILoggingEvent> events = capturedLogs(() -> loggingClient.stream(PROMPT, chunk -> {}));

    assertTrue(warningsAndErrors(events).isEmpty(), events.toString());
  }

  /**
   * The caller that handles the exception logs it with its stack trace; this client only adds the
   * duration and the exception type and message, so a failure is not logged with a stack trace
   * twice but its cause is never lost.
   */
  private static void assertSingleFailureLog(
      List<ILoggingEvent> events, Level level, String messageStart, String cause) {
    List<ILoggingEvent> failures = warningsAndErrors(events);
    assertEquals(1, failures.size(), failures.toString());
    ILoggingEvent failure = failures.getFirst();
    String message = failure.getFormattedMessage();
    assertEquals(level, failure.getLevel());
    assertTrue(message.startsWith(messageStart), message);
    assertTrue(message.contains(cause), message);
    assertNull(failure.getThrowableProxy());
  }

  private static void assertConsumerStopLoggedAtDebugOnly(
      List<ILoggingEvent> events, String cause) {
    assertTrue(warningsAndErrors(events).isEmpty(), events.toString());
    assertTrue(
        events.stream()
            .anyMatch(
                event ->
                    event.getLevel() == Level.DEBUG
                        && event.getFormattedMessage().startsWith("LLM streaming stopped by")
                        && event.getFormattedMessage().contains(cause)
                        && event.getThrowableProxy() == null),
        events.toString());
  }

  private static List<ILoggingEvent> warningsAndErrors(List<ILoggingEvent> events) {
    return events.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
  }

  private static List<ILoggingEvent> capturedLogs(Runnable action) {
    Logger logger = (Logger) LoggerFactory.getLogger(LoggingLlmClient.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }
    return appender.list;
  }
}
