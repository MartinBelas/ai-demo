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
import ai.demo.exception.LlmCommunicationException;
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
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OllamaClientTest {

  private HttpTransport httpTransport;
  private OllamaClient ollamaClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {

    AppConfig config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.7, 100, "Be helpful."),
            new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2),
            null,
            Path.of("conversation.json"));

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

    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamResponse.getBytes(StandardCharsets.UTF_8));

    HttpResponse<java.io.InputStream> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(inputStream);
    when(httpTransport.sendStreaming(any())).thenReturn(httpResponse);

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
      assertEquals(ChatChunkType.THINKING, chunks.get(0).type());
      assertFalse(chunks.get(0).finished());

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

    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamResponse.getBytes(StandardCharsets.UTF_8));

    HttpResponse<java.io.InputStream> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(inputStream);
    when(httpTransport.sendStreaming(any())).thenReturn(httpResponse);

    List<ChatChunk> chunks = new ArrayList<>();

    StreamingResult result = ollamaClient.stream(createPrompt(), chunks::add);

    assertEquals("qwen3:4b", result.model());

    assertEquals(2, chunks.size());

    assertEquals("Thinking...", chunks.getFirst().content());
    assertEquals(ChatChunkType.THINKING, chunks.get(0).type());
    assertFalse(chunks.get(0).finished());

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

  private static Prompt createPrompt() {
    return new Prompt(List.of(ChatMessage.user("Hi")));
  }
}
