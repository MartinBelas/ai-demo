package ai.demo.client.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.GeminiConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeminiClientTest {

  private HttpTransport transport;
  private GeminiClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    var config =
        new AppConfig(
            LlmProvider.GEMINI,
            new GenerationConfig(0.4, 300, "Be helpful."),
            null,
            null,
            null,
            new GeminiConfig(
                "gemini-test",
                "https://generativelanguage.googleapis.com/v1beta",
                "GEMINI_API_KEY"),
            Path.of("conversation.json"));
    client = new GeminiClient(config, "secret", transport, new ObjectMapper());
  }

  @Test
  void shouldMapResponseUsageAndRequest() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}],"usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}
            """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    var result =
        client.chat(new Prompt(List.of(ChatMessage.system("Rules"), ChatMessage.user("Hi"))));

    assertEquals("Hello", result.text());
    assertEquals(10, result.tokenUsage().totalTokens());
  }

  @Test
  void shouldMapStreamingContentAndThinking() throws Exception {
    String events =
        """
        data: {"candidates":[{"content":{"parts":[{"text":"Think","thought":true}]}}]}

        data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}],"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2}}

        """;
    HttpResponse<java.io.InputStream> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
    when(transport.sendStreaming(any(HttpRequest.class))).thenReturn(response);
    List<ChatChunk> chunks = new ArrayList<>();

    var result = client.stream(new Prompt(List.of(ChatMessage.user("Hi"))), chunks::add);

    assertEquals(ChatChunkType.THINKING, chunks.getFirst().type());
    assertEquals(ChatChunkType.CONTENT, chunks.getLast().type());
    assertEquals(7, result.tokenUsage().totalTokens());
    assertTrue(result.model().contains("gemini"));
  }

  @Test
  void shouldCloseStreamingBodyWhenProviderReturnsError() throws Exception {
    java.io.InputStream body = mock(java.io.InputStream.class);
    HttpResponse<java.io.InputStream> response = mock();
    when(response.statusCode()).thenReturn(500);
    when(response.body()).thenReturn(body);
    when(transport.sendStreaming(any(HttpRequest.class))).thenReturn(response);

    assertThrows(
        ai.demo.exception.LlmCommunicationException.class,
        () -> client.stream(new Prompt(List.of(ChatMessage.user("Hi"))), chunk -> {}));

    verify(body).close();
  }
}
