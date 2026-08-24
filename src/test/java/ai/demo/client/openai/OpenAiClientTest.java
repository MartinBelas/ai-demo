package ai.demo.client.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OpenAiConfig;
import ai.demo.exception.LlmCommunicationException;
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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {

  private HttpTransport transport;
  private OpenAiClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    var config =
        new AppConfig(
            LlmProvider.OPENAI,
            new GenerationConfig(0.4, 300, "Be helpful."),
            null,
            new OpenAiConfig("test-model", "https://api.openai.com/v1", "OPENAI_API_KEY"),
            Path.of("conversation.json"));
    client = new OpenAiClient(config, "secret", transport, new ObjectMapper());
  }

  @Test
  void shouldMapResponseAndUsage() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"model":"test-model","output":[{"type":"message","content":[{"type":"output_text","text":"Hello"}]}],"usage":{"input_tokens":12,"output_tokens":5}}
            """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    var result = client.chat(prompt());

    assertEquals("Hello", result.text());
    assertEquals(12, result.tokenUsage().promptTokens());
    assertEquals(5, result.tokenUsage().completionTokens());
  }

  @Test
  void shouldMapStreamingEvents() throws Exception {
    String events =
        """
        data: {"type":"response.reasoning_summary_text.delta","delta":"Thinking"}

        data: {"type":"response.output_text.delta","delta":"Hel"}

        data: {"type":"response.output_text.delta","delta":"lo"}

        data: {"type":"response.completed","response":{"model":"test-model","usage":{"input_tokens":8,"output_tokens":3}}}

        data: [DONE]

        """;
    HttpResponse<java.io.InputStream> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
    when(transport.sendStreaming(any(HttpRequest.class))).thenReturn(response);
    List<ChatChunk> chunks = new ArrayList<>();

    var result = client.stream(prompt(), chunks::add);

    assertEquals(3, chunks.size());
    assertEquals(ChatChunkType.THINKING, chunks.getFirst().type());
    assertEquals("Hello", chunks.get(1).content() + chunks.get(2).content());
    assertEquals(11, result.tokenUsage().totalTokens());
  }

  @Test
  void shouldRejectHttpErrorAndMissingOutput() throws Exception {
    HttpResponse<String> response = mock();
    Prompt request = prompt();
    when(response.statusCode()).thenReturn(401);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);
    assertThrows(LlmCommunicationException.class, () -> client.chat(request));

    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"output\":[]}");
    assertThrows(LlmCommunicationException.class, () -> client.chat(request));
  }

  @Test
  void shouldCloseStreamingBodyWhenProviderReturnsError() throws Exception {
    java.io.InputStream body = mock(java.io.InputStream.class);
    HttpResponse<java.io.InputStream> response = mock();
    when(response.statusCode()).thenReturn(429);
    when(response.body()).thenReturn(body);
    when(transport.sendStreaming(any(HttpRequest.class))).thenReturn(response);
    Prompt request = prompt();
    Consumer<ChatChunk> chunkConsumer = chunk -> {};

    assertThrows(LlmCommunicationException.class, () -> client.stream(request, chunkConsumer));

    verify(body).close();
  }

  private Prompt prompt() {
    return new Prompt(List.of(ChatMessage.user("Hello")));
  }
}
