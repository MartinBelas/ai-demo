package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.ToolCallEvent;
import ai.demo.agent.ToolResultEvent;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.config.OllamaConfig;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ApiServerTest {

  private static final String APPLICATION_JSON = "application/json";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String EVENT_STREAM = "text/event-stream";
  private static final String LOCAL_SERVER = "http://localhost:";
  private static final String OLLAMA_MODEL = "qwen3:4b";
  private static final String PROVIDER_FAILURE_DETAILS = "Secret provider details";
  private static final String SAFE_LLM_ERROR = "Unable to communicate with the AI model.";

  @Test
  void shouldExposeHealthEndpoint() throws IOException, InterruptedException {
    try (ApiServer server = new ApiServer(0);
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/health"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals(APPLICATION_JSON, response.headers().firstValue(CONTENT_TYPE).orElseThrow());
      assertEquals("{\"status\":\"UP\"}", response.body());

      server.close();
      server.awaitShutdown();
    }
  }

  @Test
  void shouldExposeOpenApiSpecification() throws IOException, InterruptedException {
    try (ApiServer server = new ApiServer(0);
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/openapi.yaml"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("application/yaml", response.headers().firstValue(CONTENT_TYPE).orElseThrow());
      try (var inputStream = getClass().getResourceAsStream("/openapi.yaml")) {
        assertNotNull(inputStream);
        assertEquals(
            new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), response.body());
      }
    }
  }

  @Test
  void shouldExposeAvailableProviders() throws IOException, InterruptedException {
    AppConfig config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.4, 1000, "Be helpful."),
            new OllamaConfig(OLLAMA_MODEL, "http://localhost:11434", 4096, 1.18),
            null,
            Path.of("conversation.json"));
    LlmProviderAvailability availability = new LlmProviderAvailability(config, key -> null);
    try (ApiServer server = new ApiServer(0, availability, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/llm/providers"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals(APPLICATION_JSON, response.headers().firstValue(CONTENT_TYPE).orElseThrow());
      assertEquals(
          "{\"providers\":[{\"id\":\"OLLAMA\",\"model\":\"" + OLLAMA_MODEL + "\"}]}",
          response.body());
    }
  }

  @Test
  void shouldAnswerStatelessChatRequest() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ChatResponse("Hello!", OLLAMA_MODEL, new TokenUsage(12, 4), 25));

    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              chatRequest(
                  server,
                  """
                  {"provider":"OLLAMA","messages":[{"role":"USER","content":"Hello"}]}
                  """),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals(
          "{\"answer\":\"Hello!\",\"model\":\""
              + OLLAMA_MODEL
              + "\",\"tokenUsage\":{"
              + "\"promptTokens\":12,\"completionTokens\":4,\"totalTokens\":16},\"durationMs\":25}",
          response.body());
    }
  }

  @Test
  void shouldRejectChatRequestWithoutMessages() throws IOException, InterruptedException {
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              chatRequest(server, "{\"provider\":\"OLLAMA\",\"messages\":[]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(400, response.statusCode());
      assertEquals(
          "{\"code\":\"INVALID_REQUEST\",\"message\":\"Invalid chat request.\","
              + "\"details\":[{\"field\":\"messages\",\"message\":"
              + "\"At least one chat message is required.\"}]}",
          response.body());
      verifyNoInteractions(resolver);
    }
  }

  @Test
  void shouldReturnSafeErrorForUnavailableProvider() throws IOException, InterruptedException {
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.GROQ))
        .thenThrow(new ConfigurationException("Provider GROQ is not configured"));
    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              chatRequest(
                  server,
                  "{\"provider\":\"GROQ\",\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(400, response.statusCode());
      assertEquals(
          "{\"code\":\"LLM_PROVIDER_UNAVAILABLE\",\"message\":"
              + "\"The selected LLM provider is not available.\"}",
          response.body());
    }
  }

  @Test
  void shouldIdentifyInvalidMessageRole() throws IOException, InterruptedException {
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              chatRequest(
                  server, "{\"messages\":[{\"role\":\"SYSTEM\",\"content\":\"Be helpful\"}]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(400, response.statusCode());
      assertTrue(response.body().contains("\"field\":\"messages[0].role\""));
      assertTrue(response.body().contains("Role is invalid."));
      verifyNoInteractions(resolver);
    }
  }

  @Test
  void shouldReturnSafeErrorWhenLlmCommunicationFails() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new LlmCommunicationException(PROVIDER_FAILURE_DETAILS));
    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              chatRequest(server, "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(502, response.statusCode());
      assertEquals(
          "{\"code\":\"LLM_COMMUNICATION_ERROR\",\"message\":" + "\"" + SAFE_LLM_ERROR + "\"}",
          response.body());
    }
  }

  @Test
  void shouldStreamTypedChatEventsInOrder() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<AgentEvent> eventConsumer = invocation.getArgument(1);
              eventConsumer.accept(new ThinkingEvent("Checking"));
              eventConsumer.accept(new ToolCallEvent("calculator", "3+5"));
              eventConsumer.accept(new ToolResultEvent("calculator", "8"));
              eventConsumer.accept(new ContentEvent("Hello!"));
              return new ChatResponse("Hello!", OLLAMA_MODEL, new TokenUsage(12, 4), 25);
            });

    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              streamingChatRequest(
                  server, "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(
          response.headers().firstValue(CONTENT_TYPE).orElseThrow().startsWith(EVENT_STREAM));
      String body = response.body();
      int thinking = body.indexOf("event: thinking\ndata: {\"content\":\"Checking\"}");
      int toolStarted =
          body.indexOf("event: tool\ndata: {\"name\":\"calculator\",\"status\":\"RUNNING\"}");
      int toolCompleted =
          body.indexOf("event: tool\ndata: {\"name\":\"calculator\",\"status\":\"COMPLETED\"}");
      int content = body.indexOf("event: content\ndata: {\"content\":\"Hello!\"}");
      int completion = body.indexOf("event: completion\ndata: {\"model\":\"" + OLLAMA_MODEL + "\"");
      assertTrue(thinking >= 0);
      assertTrue(toolStarted > thinking);
      assertTrue(toolCompleted > toolStarted);
      assertTrue(content > toolCompleted);
      assertTrue(completion > content);
    }
  }

  @Test
  void shouldDeliverThinkingBeforeChatCompletes() throws Exception {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    CountDownLatch thinkingSent = new CountDownLatch(1);
    CountDownLatch allowCompletion = new CountDownLatch(1);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<AgentEvent> eventConsumer = invocation.getArgument(1);
              eventConsumer.accept(new ThinkingEvent("Checking"));
              thinkingSent.countDown();
              assertTrue(allowCompletion.await(5, TimeUnit.SECONDS));
              eventConsumer.accept(new ContentEvent("Hello!"));
              return new ChatResponse("Hello!", OLLAMA_MODEL, new TokenUsage(12, 4), 25);
            });

    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      CompletableFuture<HttpResponse<InputStream>> responseFuture =
          client.sendAsync(
              streamingChatRequest(
                  server, "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}"),
              HttpResponse.BodyHandlers.ofInputStream());

      assertTrue(thinkingSent.await(2, TimeUnit.SECONDS));
      HttpResponse<InputStream> response = responseFuture.orTimeout(2, TimeUnit.SECONDS).join();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));

      assertEquals("event: thinking", reader.readLine());
      assertEquals("data: {\"content\":\"Checking\"}", reader.readLine());
      assertEquals("", reader.readLine());
      allowCompletion.countDown();
      assertTrue(responseFuture.orTimeout(2, TimeUnit.SECONDS).isDone());
    } finally {
      allowCompletion.countDown();
    }
  }

  @Test
  void shouldStreamSafeTerminalErrorWhenLlmFails() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenThrow(new LlmCommunicationException(PROVIDER_FAILURE_DETAILS));

    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              streamingChatRequest(
                  server, "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}"),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("event: error"));
      assertTrue(response.body().contains("\"code\":\"LLM_COMMUNICATION_ERROR\""));
      assertTrue(response.body().contains(SAFE_LLM_ERROR));
      assertFalse(response.body().contains(PROVIDER_FAILURE_DETAILS));
    }
  }

  private HttpRequest chatRequest(ApiServer server, String body) {
    return HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/chat"))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private HttpRequest streamingChatRequest(ApiServer server, String body) {
    return HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/chat/stream"))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .header("Accept", EVENT_STREAM)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }
}
