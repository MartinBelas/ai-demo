package ai.demo.client.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.GeminiConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.TestAppConfig;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeminiClientTest {

  private HttpTransport transport;
  private GeminiClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    var config =
        TestAppConfig.builder(LlmProvider.GEMINI)
            .gemini(
                new GeminiConfig(
                    "gemini-test",
                    "https://generativelanguage.googleapis.com/v1beta",
                    "GEMINI_API_KEY"))
            .build();
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
  void shouldMergeMultipleSystemMessagesIntoOneSystemInstruction() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}],"usageMetadata":{}}
            """);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(transport.send(captor.capture())).thenReturn(response);

    client.chat(
        new Prompt(
            List.of(
                ChatMessage.system("General rules"),
                ChatMessage.system("RAG instructions"),
                ChatMessage.user("Hi"))));

    JsonNode body = new ObjectMapper().readTree(bodyOf(captor.getValue()));
    JsonNode systemParts = body.path("systemInstruction").path("parts");
    assertEquals(2, systemParts.size());
    assertEquals("General rules", systemParts.get(0).path("text").asText());
    assertEquals("RAG instructions", systemParts.get(1).path("text").asText());
    assertEquals(1, body.path("contents").size());
  }

  @Test
  void shouldKeepLaterSystemMessageNextToTheFollowingUserMessage() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}],"usageMetadata":{}}
            """);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(transport.send(captor.capture())).thenReturn(response);

    client.chat(
        new Prompt(
            List.of(
                ChatMessage.system("General rules"),
                ChatMessage.user("First question"),
                ChatMessage.assistant("First answer"),
                ChatMessage.system("RAG sources"),
                ChatMessage.user("Second question"))));

    JsonNode body = new ObjectMapper().readTree(bodyOf(captor.getValue()));
    JsonNode systemParts = body.path("systemInstruction").path("parts");
    assertEquals(1, systemParts.size());
    assertEquals("General rules", systemParts.get(0).path("text").asText());
    JsonNode contents = body.path("contents");
    assertEquals(3, contents.size());
    JsonNode last = contents.get(2);
    assertEquals("user", last.path("role").asText());
    assertEquals("RAG sources", last.path("parts").get(0).path("text").asText());
    assertEquals("Second question", last.path("parts").get(1).path("text").asText());
  }

  @Test
  void shouldAttachLaterSystemMessageToFollowingToolTurn() throws Exception {
    JsonNode contents =
        chatRequestBody(
                ChatMessage.user("Question"),
                ChatMessage.assistant("Calling tool"),
                ChatMessage.system("Extra rules"),
                ChatMessage.tool("calculator", "4"))
            .path("contents");

    assertEquals(3, contents.size());
    JsonNode last = contents.get(2);
    assertEquals("user", last.path("role").asText());
    assertEquals("Extra rules", last.path("parts").get(0).path("text").asText());
    assertEquals("Tool result:\n4", last.path("parts").get(1).path("text").asText());
  }

  @Test
  void shouldAppendTrailingSystemMessageToLastUserTurn() throws Exception {
    JsonNode contents =
        chatRequestBody(ChatMessage.user("Question"), ChatMessage.system("Late rules"))
            .path("contents");

    assertEquals(1, contents.size());
    JsonNode parts = contents.get(0).path("parts");
    assertEquals("Question", parts.get(0).path("text").asText());
    assertEquals("Late rules", parts.get(1).path("text").asText());
  }

  @Test
  void shouldAddTrailingSystemMessageAsUserTurnAfterModelTurn() throws Exception {
    JsonNode contents =
        chatRequestBody(
                ChatMessage.user("Question"),
                ChatMessage.assistant("Answer"),
                ChatMessage.system("Late rules"))
            .path("contents");

    assertEquals(3, contents.size());
    assertEquals("model", contents.get(1).path("role").asText());
    assertEquals("user", contents.get(2).path("role").asText());
    assertEquals("Late rules", contents.get(2).path("parts").get(0).path("text").asText());
  }

  @Test
  void shouldKeepLaterSystemMessageNextToQuestionWhenStreaming() throws Exception {
    HttpResponse<java.io.InputStream> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(transport.sendStreaming(captor.capture())).thenReturn(response);

    client.stream(
        new Prompt(
            List.of(
                ChatMessage.user("First question"),
                ChatMessage.assistant("First answer"),
                ChatMessage.system("RAG sources"),
                ChatMessage.user("Second question"))),
        chunk -> {});

    JsonNode contents = new ObjectMapper().readTree(bodyOf(captor.getValue())).path("contents");
    assertEquals(3, contents.size());
    assertEquals("RAG sources", contents.get(2).path("parts").get(0).path("text").asText());
    assertEquals("Second question", contents.get(2).path("parts").get(1).path("text").asText());
  }

  private JsonNode chatRequestBody(ChatMessage... messages) throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}],"usageMetadata":{}}
            """);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(transport.send(captor.capture())).thenReturn(response);

    client.chat(new Prompt(List.of(messages)));

    return new ObjectMapper().readTree(bodyOf(captor.getValue()));
  }

  private static String bodyOf(HttpRequest request) throws Exception {
    CompletableFuture<String> collected = new CompletableFuture<>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<>() {
              private final StringBuilder text = new StringBuilder();

              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer item) {
                text.append(StandardCharsets.UTF_8.decode(item));
              }

              @Override
              public void onError(Throwable throwable) {
                collected.completeExceptionally(throwable);
              }

              @Override
              public void onComplete() {
                collected.complete(text.toString());
              }
            });
    return collected.get(1, TimeUnit.SECONDS);
  }

  @Test
  void shouldCloseStreamingBodyWhenProviderReturnsError() throws Exception {
    java.io.InputStream body = mock(java.io.InputStream.class);
    when(body.readAllBytes()).thenReturn(new byte[0]);
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
