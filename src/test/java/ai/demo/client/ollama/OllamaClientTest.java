package ai.demo.client.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmResponse;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
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

    AppConfig config = new AppConfig("http://localhost:11434", "qwen3:4b", 0.7, 100, 4096, 1.2);

    httpTransport = mock(HttpTransport.class);

    ollamaClient = new OllamaClient(config, httpTransport, objectMapper);
  }

  @Test
  void shouldStreamAndSkipEmptyAndIgnoreUnknownFields() throws Exception {

    String streamResponse =
        """
                    {\
                    "model": "qwen3:4b", \
                    "message": {"role": "assistant", "content": ""}, \
                    "done": false}
                    {\
                    "model": "qwen3:4b", \
                    "message": {"role": "assistant", "content": "Hel", "thinking": true}, \
                    "done": false}
                    {\
                    "model": "qwen3:4b", \
                    "message": {"role": "assistant", "content": "lo"}, \
                    "done": true}
                    """;

    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamResponse.getBytes(StandardCharsets.UTF_8));

    HttpResponse<java.io.InputStream> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(inputStream);
    when(httpTransport.sendStreaming(any())).thenReturn(httpResponse);

    Logger logger = (Logger) LoggerFactory.getLogger(ai.demo.client.ollama.OllamaClient.class);

    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    listAppender.start();

    logger.setLevel(Level.DEBUG);
    logger.addAppender(listAppender);

    List<ChatChunk> chunks = new ArrayList<>();

    ollamaClient.stream(createPrompt(), chunks::add);

    assertEquals(2, chunks.size());
    assertEquals("Hel", chunks.get(0).content());
    assertFalse(chunks.get(0).finished());
    assertEquals("lo", chunks.get(1).content());
    assertTrue(chunks.get(1).finished());

    boolean found =
        listAppender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel().equals(Level.DEBUG)
                        && e.getFormattedMessage().contains("Skipping empty streaming chunk"));

    assertTrue(found);

    logger.detachAppender(listAppender);
  }

  @Test
  void shouldReturnLlmResponseWhenOllamaReturnsSuccess() throws Exception {

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
  void shouldReturnZeroTokenUsageWhenOllamaDoesNotProvideTokenCounts() throws Exception {

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
  void shouldThrowExceptionWhenOllamaReturnsHttpError() throws Exception {

    HttpResponse<String> httpResponse = mock();

    when(httpResponse.statusCode()).thenReturn(500);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Prompt prompt = createPrompt();

    assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));
  }

  @Test
  void shouldThrowExceptionWhenCommunicationFails() throws Exception {

    when(httpTransport.send(any())).thenThrow(new IOException("Connection refused"));

    Prompt prompt = createPrompt();

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(prompt));

    assertEquals("Failed to communicate with Ollama", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenResponseIsInvalidJson() throws Exception {

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
