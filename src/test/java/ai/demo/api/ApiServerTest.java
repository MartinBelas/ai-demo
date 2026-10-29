package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.TokenUsageEvent;
import ai.demo.agent.ToolCallEvent;
import ai.demo.agent.ToolResultEvent;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.DemoLimitsConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.config.OllamaConfig;
import ai.demo.config.TestAppConfig;
import ai.demo.config.TestDemoLimits;
import ai.demo.exception.AgentDecisionException;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.InMemoryDemoQuotaStore;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class ApiServerTest {

  private static final String APPLICATION_JSON = "application/json";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String EVENT_STREAM = "text/event-stream";
  private static final String LOCAL_SERVER = "http://localhost:";
  private static final String OLLAMA_MODEL = "qwen3:4b";
  private static final String USER_MESSAGE_BODY =
      "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}";
  private static final String PROVIDER_FAILURE_DETAILS = "Secret provider details";
  private static final String INTERNAL_DETAILS = "secret-internal-tool";
  private static final DemoLimitsConfig DEMO_LIMITS =
      TestDemoLimits.enabled()
          .dailyRequests(10)
          .hourlyRequestsPerIp(10)
          .concurrentStreams(10)
          .maxInputCharacters(100)
          .maxOutputTokensPerCall(1000)
          .build();
  private static final String SAFE_LLM_ERROR =
      "Unable to communicate with the OLLAMA AI model. "
          + "Try selecting a different AI provider or try again later.";

  @Test
  void shouldExposeHealthEndpoint() throws IOException, InterruptedException {
    ApiServer closedServer;
    try (ApiServer server = new ApiServer(0);
        HttpClient client = HttpClient.newHttpClient()) {
      closedServer = server;
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/health"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals(APPLICATION_JSON, response.headers().firstValue(CONTENT_TYPE).orElseThrow());
      assertEquals("{\"status\":\"UP\"}", response.body());
    }
    // Returns only once close() has stopped the server.
    closedServer.awaitShutdown();
  }

  @Test
  void shouldServeProductionFrontend() throws IOException, InterruptedException {
    try (ApiServer server = new ApiServer(0);
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/")).GET().build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("<div id=\"app\"></div>"));
      assertTrue(response.body().contains("/assets/"));
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
        TestAppConfig.builder(LlmProvider.OLLAMA)
            .generation(new GenerationConfig(0.4, 1000, "Be helpful."))
            .ollama(new OllamaConfig(OLLAMA_MODEL, "http://localhost:11434", 4096, 1.18))
            .build();
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
    when(chatService.ask(any(), any()))
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
    when(chatService.ask(any(), any()))
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
  void shouldReturnSafeAgentErrorWhenAgentDecisionIsInvalid()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(), any()))
        .thenThrow(new AgentDecisionException("Unknown tool: " + INTERNAL_DETAILS));
    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(chatRequest(server, USER_MESSAGE_BODY), HttpResponse.BodyHandlers.ofString());

      assertEquals(502, response.statusCode());
      assertTrue(response.body().contains("\"code\":\"LLM_INVALID_RESPONSE\""));
      assertFalse(response.body().contains("Agent mode"));
      assertFalse(response.body().contains(INTERNAL_DETAILS));
    }
  }

  @Test
  void shouldStreamSafeAgentErrorWhenAgentDecisionIsInvalid()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenThrow(new AgentDecisionException("Unknown tool: " + INTERNAL_DETAILS));

    try (ApiServer server =
            new ApiServer(0, null, resolver, LlmProvider.OLLAMA, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(streamingChatRequest(server), HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("event: error"));
      assertTrue(response.body().contains("\"code\":\"LLM_INVALID_RESPONSE\""));
      assertFalse(response.body().contains("Agent mode"));
      assertFalse(response.body().contains(INTERNAL_DETAILS));
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
          client.send(streamingChatRequest(server), HttpResponse.BodyHandlers.ofString());

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
          client.sendAsync(streamingChatRequest(server), HttpResponse.BodyHandlers.ofInputStream());

      assertTrue(thinkingSent.await(2, TimeUnit.SECONDS));
      HttpResponse<InputStream> response = responseFuture.orTimeout(2, TimeUnit.SECONDS).join();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));

      assertEquals("event: thinking", reader.readLine());
      assertEquals("data: {\"content\":\"Checking\"}", reader.readLine());
      assertEquals("", reader.readLine());
      allowCompletion.countDown();
      assertTrue(responseFuture.orTimeout(2, TimeUnit.SECONDS).isDone());
      reader.transferTo(Writer.nullWriter());
      reader.close();
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
          client.send(streamingChatRequest(server), HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("event: error"));
      assertTrue(response.body().contains("\"code\":\"LLM_COMMUNICATION_ERROR\""));
      assertTrue(response.body().contains(SAFE_LLM_ERROR));
      assertFalse(response.body().contains(PROVIDER_FAILURE_DETAILS));
    }
  }

  @Test
  void shouldRejectRequestsAfterPublicDemoQuotaIsReached()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenReturn(new ChatResponse("Hello!", OLLAMA_MODEL, new TokenUsage(3, 2), 10));
    DemoLimitsConfig limits =
        TestDemoLimits.enabled()
            .dailyRequests(1)
            .hourlyRequestsPerIp(1)
            .concurrentStreams(1)
            .maxInputCharacters(100)
            .maxOutputTokensPerCall(1000)
            .build();

    try (ApiServer server =
            new ApiServer(
                0,
                null,
                resolver,
                LlmProvider.OLLAMA,
                new ObjectMapper(),
                new DemoProtection(limits, new InMemoryDemoQuotaStore(limits), "test-salt"));
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();
      String body = "{\"messages\":[{\"role\":\"USER\",\"content\":\"Hi\"}]}";

      HttpResponse<String> first =
          client.send(chatRequest(server, body), HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> second =
          client.send(chatRequest(server, body), HttpResponse.BodyHandlers.ofString());

      assertEquals(200, first.statusCode());
      assertEquals(429, second.statusCode());
      assertTrue(second.body().contains("DEMO_LIMIT_EXCEEDED"));
    }
  }

  @Test
  void shouldChargeTokensOfInvalidAgentDecisionToDemoQuota()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(usageThenFailure(new AgentDecisionException("Unknown tool")));
    InMemoryDemoQuotaStore quotaStore = new InMemoryDemoQuotaStore(DEMO_LIMITS);

    try (ApiServer server =
            new ApiServer(
                0,
                null,
                resolver,
                LlmProvider.OLLAMA,
                new ObjectMapper(),
                new DemoProtection(DEMO_LIMITS, quotaStore, "test-salt"));
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(chatRequest(server, USER_MESSAGE_BODY), HttpResponse.BodyHandlers.ofString());

      assertEquals(502, response.statusCode());
      assertEquals(500, quotaStore.snapshot(today()).tokens());
    }
  }

  @Test
  void shouldChargeTokensOfInvalidAgentDecisionToDemoQuotaWhenStreaming()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(usageThenFailure(new AgentDecisionException("Unknown tool")));
    InMemoryDemoQuotaStore quotaStore = new InMemoryDemoQuotaStore(DEMO_LIMITS);

    try (ApiServer server =
            new ApiServer(
                0,
                null,
                resolver,
                LlmProvider.OLLAMA,
                new ObjectMapper(),
                new DemoProtection(DEMO_LIMITS, quotaStore, "test-salt"));
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(streamingChatRequest(server), HttpResponse.BodyHandlers.ofString());

      assertTrue(response.body().contains("\"code\":\"LLM_INVALID_RESPONSE\""));
      assertEquals(500, quotaStore.snapshot(today()).tokens());
    }
  }

  @Test
  void shouldChargeTokensOfEarlierLlmCallsWhenProviderFailsLater()
      throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(usageThenFailure(new LlmCommunicationException("model returned no content")));
    InMemoryDemoQuotaStore quotaStore = new InMemoryDemoQuotaStore(DEMO_LIMITS);

    try (ApiServer server =
            new ApiServer(
                0,
                null,
                resolver,
                LlmProvider.OLLAMA,
                new ObjectMapper(),
                new DemoProtection(DEMO_LIMITS, quotaStore, "test-salt"));
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpResponse<String> response =
          client.send(streamingChatRequest(server), HttpResponse.BodyHandlers.ofString());

      assertTrue(response.body().contains("\"code\":\"LLM_COMMUNICATION_ERROR\""));
      assertEquals(500, quotaStore.snapshot(today()).tokens());
    }
  }

  @Test
  void shouldChargeTokensOfFinishedLlmCallsWhenStreamingClientDisconnects() throws Exception {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    CountDownLatch thinkingSent = new CountDownLatch(1);
    CountDownLatch clientGone = new CountDownLatch(1);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(any(Conversation.class), any()))
        .thenAnswer(
            invocation -> {
              Consumer<AgentEvent> events = invocation.getArgument(1);
              events.accept(new TokenUsageEvent(new TokenUsage(300, 200)));
              events.accept(new ThinkingEvent("Checking"));
              thinkingSent.countDown();
              // Runs on the server's request thread, where a failed assertion would go unnoticed;
              // the test thread always counts down (also in its finally block), so this cannot
              // hang.
              clientGone.await();
              // Writes right after the reset may still succeed; the first failing write ends the
              // request with a disconnect. Finishing normally would charge the response instead.
              for (int i = 0; i < 300; i++) {
                events.accept(new ThinkingEvent("still thinking"));
                // The server only learns about the reset from a failing write, so writes must be
                // spaced out; there is no event to wait for instead.
                Thread.sleep(10); // NOSONAR java:S2925
              }
              return new ChatResponse("Hello!", OLLAMA_MODEL, new TokenUsage(1, 1), 25);
            });
    InMemoryDemoQuotaStore quotaStore = spy(new InMemoryDemoQuotaStore(DEMO_LIMITS));

    try (ApiServer server =
        new ApiServer(
            0,
            null,
            resolver,
            LlmProvider.OLLAMA,
            new ObjectMapper(),
            new DemoProtection(DEMO_LIMITS, quotaStore, "test-salt"))) {
      server.start();

      disconnectAfterFirstEvent(server, thinkingSent);
      clientGone.countDown();

      // The charge happens on the server thread after it notices the disconnect.
      verify(quotaStore, timeout(10_000)).recordUsage(any(), eq(500));
    } finally {
      clientGone.countDown();
    }
  }

  private void disconnectAfterFirstEvent(ApiServer server, CountDownLatch firstEventSent)
      throws IOException, InterruptedException {
    byte[] body = USER_MESSAGE_BODY.getBytes(StandardCharsets.UTF_8);
    String head =
        "POST /api/chat/stream HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Content-Type: application/json\r\n"
            + "Accept: text/event-stream\r\n"
            + "Connection: close\r\n"
            + "Content-Length: "
            + body.length
            + "\r\n\r\n";
    try (Socket socket = new Socket("localhost", server.port())) {
      // A zero linger time makes close() send a reset, so the server notices the loss at once.
      socket.setSoLinger(true, 0);
      OutputStream request = socket.getOutputStream();
      request.write(head.getBytes(StandardCharsets.UTF_8));
      request.write(body);
      request.flush();
      assertTrue(firstEventSent.await(5, TimeUnit.SECONDS));
    }
  }

  /** Like the real agent: reports the usage of an LLM call (300 + 200 tokens), then fails. */
  private static Answer<ChatResponse> usageThenFailure(RuntimeException failure) {
    return invocation -> {
      Consumer<AgentEvent> events = invocation.getArgument(1);
      events.accept(new TokenUsageEvent(new TokenUsage(300, 200)));
      throw failure;
    };
  }

  private static String today() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  private HttpRequest chatRequest(ApiServer server, String body) {
    return HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/chat"))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private HttpRequest streamingChatRequest(ApiServer server) {
    return HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/chat/stream"))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .header("Accept", EVENT_STREAM)
        .POST(HttpRequest.BodyPublishers.ofString(USER_MESSAGE_BODY))
        .build();
  }
}
