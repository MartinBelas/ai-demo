package ai.demo.client.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.config.TestAppConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.exception.LlmErrorCategory;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.prompt.Prompt;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class OllamaClientTest {

  private HttpTransport httpTransport;
  private OllamaClient ollamaClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {

    AppConfig config =
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(new GenerationConfig(0.7, 100, "Be helpful."))
            .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2))
            .build();

    httpTransport = mock(HttpTransport.class);

    ollamaClient = new OllamaClient(config, httpTransport, objectMapper);
  }

  @Test
  void shouldStreamThinkingAndContentAndSkipEmptyChunks() throws IOException, InterruptedException {

    String streamResponse =
        """
            {"model":"qwen3:4b","message":{"role":"assistant","content":""},"done":false}
            {"model":"qwen3:4b","message":{"role":"assistant","content":"","thinking":"Let me think..."},"done":false}
            {"model":"qwen3:4b","message":{"role":"assistant","content":"Hel"},"done":false}
            {"model":"qwen3:4b","message":{"role":"assistant","content":"lo"},"done":true}
            """;

    stubSuccessfulStream(streamResponse);

    Logger logger = (Logger) LoggerFactory.getLogger(OllamaClient.class);

    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    Level originalLevel = logger.getLevel();

    listAppender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(listAppender);

    try {
      List<ChatChunk> chunks = new ArrayList<>();

      StreamingResult result = ollamaClient.stream(createPrompt(), chunks::add);

      assertEquals("qwen3:4b", result.model());
      assertEquals(0, result.tokenUsage().totalTokens());

      assertEquals(3, chunks.size());

      assertEquals("Let me think...", chunks.getFirst().content());
      assertEquals(ChatChunkType.THINKING, chunks.getFirst().type());
      assertFalse(chunks.getFirst().finished());

      assertEquals("Hel", chunks.get(1).content());
      assertEquals(ChatChunkType.CONTENT, chunks.get(1).type());
      assertFalse(chunks.get(1).finished());

      assertEquals("lo", chunks.get(2).content());
      assertEquals(ChatChunkType.CONTENT, chunks.get(2).type());
      assertTrue(chunks.get(2).finished());

      boolean skippedChunkLogged =
          listAppender.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel().equals(Level.DEBUG)
                          && event
                              .getFormattedMessage()
                              .contains("Skipping empty streaming chunk"));

      assertTrue(skippedChunkLogged);

    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
      listAppender.stop();
    }
  }

  @Test
  void shouldStreamThinkingAndContentFromSameChunk() throws IOException, InterruptedException {

    String streamResponse =
        """
            {"model":"qwen3:4b","message":{"role":"assistant","thinking":"Thinking...","content":"Answer"},"done":true}
            """;

    stubSuccessfulStream(streamResponse);

    List<ChatChunk> chunks = new ArrayList<>();

    StreamingResult result = ollamaClient.stream(createPrompt(), chunks::add);

    assertEquals("qwen3:4b", result.model());

    assertEquals(2, chunks.size());

    assertEquals("Thinking...", chunks.getFirst().content());
    assertEquals(ChatChunkType.THINKING, chunks.getFirst().type());
    assertFalse(chunks.getFirst().finished());

    assertEquals("Answer", chunks.get(1).content());
    assertEquals(ChatChunkType.CONTENT, chunks.get(1).type());
    assertTrue(chunks.get(1).finished());
  }

  @Test
  void shouldReturnLlmResponseWhenOllamaReturnsSuccess() throws IOException, InterruptedException {

    String jsonResponse =
        """
            {
              "model": "qwen3:4b",
              "message": {
                "role": "assistant",
                "content": "Hello"
              },
              "done": true,
              "prompt_eval_count": 100,
              "eval_count": 50
            }
            """;

    HttpResponse<String> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(jsonResponse);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    LlmResponse response = ollamaClient.chat(createPrompt());

    assertEquals("Hello", response.text());
    assertEquals("qwen3:4b", response.model());
    assertEquals(100, response.tokenUsage().promptTokens());
    assertEquals(50, response.tokenUsage().completionTokens());
    assertEquals(150, response.tokenUsage().totalTokens());
  }

  @Test
  void shouldReturnZeroTokenUsageWhenOllamaDoesNotProvideTokenCounts()
      throws IOException, InterruptedException {

    String jsonResponse =
        """
            {
              "model": "qwen3:4b",
              "message": {
                "role": "assistant",
                "content": "Hello"
              },
              "done": true
            }
            """;

    HttpResponse<String> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(jsonResponse);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    LlmResponse response = ollamaClient.chat(createPrompt());

    assertEquals(0, response.tokenUsage().promptTokens());
    assertEquals(0, response.tokenUsage().completionTokens());
    assertEquals(0, response.tokenUsage().totalTokens());
  }

  @Test
  void shouldThrowExceptionWhenOllamaReturnsHttpError() throws IOException, InterruptedException {

    HttpResponse<String> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(500);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Prompt prompt = createPrompt();

    assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));
  }

  static Stream<Arguments> chatErrorBodies() {
    return Stream.of(
        Arguments.of(
            404,
            "{\"error\":\"model 'qwen3:4b' not found\"}",
            "Ollama returned HTTP status 404: model 'qwen3:4b' not found"),
        Arguments.of(
            500,
            "{\"error\":{\"message\":\"boom\",\"code\":1}}",
            "Ollama returned HTTP status 500: boom"),
        Arguments.of(
            500,
            "{\"error\":{\"code\":1}}",
            "Ollama returned HTTP status 500: {\"error\":{\"code\":1}}"),
        Arguments.of(502, "Bad Gateway", "Ollama returned HTTP status 502: Bad Gateway"),
        Arguments.of(
            502, "x".repeat(2000), "Ollama returned HTTP status 502: " + "x".repeat(500) + "..."));
  }

  @ParameterizedTest
  @MethodSource("chatErrorBodies")
  void shouldReportOllamaErrorBodyWhenChatFails(int statusCode, String body, String expected)
      throws IOException, InterruptedException {
    stubChatError(statusCode, body);

    assertEquals(expected, chatFailureMessage());
  }

  @Test
  void shouldCloseStreamingBodyAndReportErrorWhenOllamaReturnsHttpError()
      throws IOException, InterruptedException {
    TrackingInputStream body = new TrackingInputStream("{\"error\":\"server overloaded\"}");
    stubSendStreaming(503, body);
    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(
            LlmCommunicationException.class, () -> ollamaClient.stream(prompt, chunk -> {}));

    assertEquals("Ollama returned HTTP status 503: server overloaded", exception.getMessage());
    assertTrue(body.closed, "error response body must be closed to release the connection");
  }

  @Test
  void shouldCategorizeOllamaRateLimit() throws IOException, InterruptedException {
    stubSendStreaming(429, new ByteArrayInputStream(new byte[0]));
    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(
            LlmCommunicationException.class, () -> ollamaClient.stream(prompt, chunk -> {}));

    assertEquals(LlmErrorCategory.RATE_LIMIT, exception.category());
    assertEquals("Ollama returned HTTP status 429: no response body", exception.getMessage());
  }

  @Test
  void shouldCloseStreamingBodyAfterSuccessfulStream() throws IOException, InterruptedException {
    TrackingInputStream body =
        new TrackingInputStream(
            "{\"model\":\"qwen3:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},"
                + "\"done\":true}\n");
    stubSendStreaming(200, body);

    ollamaClient.stream(createPrompt(), chunk -> {});

    assertTrue(body.closed);
  }

  @Test
  void shouldCategorizeOllamaAuthenticationFailure() throws IOException, InterruptedException {
    stubChatError(401, "{\"error\":\"unauthorized\"}");

    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));

    assertEquals(LlmErrorCategory.AUTHENTICATION, exception.category());
  }

  @Test
  void shouldReportUnreadableErrorBodyAndCloseIt() throws IOException, InterruptedException {
    FailingInputStream body = new FailingInputStream();
    stubSendStreaming(503, body);
    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(
            LlmCommunicationException.class, () -> ollamaClient.stream(prompt, chunk -> {}));

    assertEquals(
        "Ollama returned HTTP status 503: response body could not be read", exception.getMessage());
    assertTrue(body.closed);
  }

  @Test
  void shouldDecodeStreamedContentAsUtf8() throws IOException, InterruptedException {
    TrackingInputStream body =
        new TrackingInputStream(
            "{\"model\":\"qwen3:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"Příliš"
                + " žluťoučký kůň\"},\"done\":true}\n");
    stubSendStreaming(200, body);
    List<ChatChunk> chunks = new ArrayList<>();

    ollamaClient.stream(createPrompt(), chunks::add);

    assertEquals("Příliš žluťoučký kůň", chunks.getFirst().content());
  }

  @Test
  void shouldThrowExceptionWhenCommunicationFails() throws IOException, InterruptedException {

    when(httpTransport.send(any())).thenThrow(new IOException("Connection refused"));

    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));

    assertEquals("Failed to communicate with Ollama", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenResponseIsInvalidJson() throws IOException, InterruptedException {

    HttpResponse<String> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("{ invalid json }");
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));

    assertEquals("Failed to communicate with Ollama", exception.getMessage());
  }

  private void stubChatError(int statusCode, String body) throws IOException, InterruptedException {
    HttpResponse<String> httpResponse = mock();
    when(httpResponse.statusCode()).thenReturn(statusCode);
    when(httpResponse.body()).thenReturn(body);
    when(httpTransport.send(any())).thenReturn(httpResponse);
  }

  private String chatFailureMessage() {
    Prompt prompt = createPrompt();
    return assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt))
        .getMessage();
  }

  private void stubSuccessfulStream(String body) throws IOException, InterruptedException {
    stubSendStreaming(200, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

  private void stubSendStreaming(int statusCode, InputStream body)
      throws IOException, InterruptedException {
    HttpResponse<InputStream> httpResponse = mock();
    when(httpResponse.statusCode()).thenReturn(statusCode);
    when(httpResponse.body()).thenReturn(body);
    when(httpTransport.sendStreaming(any())).thenReturn(httpResponse);
  }

  private static Prompt createPrompt() {
    return new Prompt(List.of(ChatMessage.user("Hi")));
  }

  private static final class TrackingInputStream extends ByteArrayInputStream {
    private boolean closed;

    private TrackingInputStream(String content) {
      super(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }

  private static final class FailingInputStream extends InputStream {
    private boolean closed;

    @Override
    public int read() throws IOException {
      throw new IOException("connection reset");
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
