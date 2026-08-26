package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.config.OllamaConfig;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApiServerTest {

  @Test
  void shouldExposeHealthEndpoint() throws IOException, InterruptedException {
    try (ApiServer server = new ApiServer(0);
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/health"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
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
          HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/openapi.yaml"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("application/yaml", response.headers().firstValue("Content-Type").orElseThrow());
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
            new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.18),
            null,
            Path.of("conversation.json"));
    LlmProviderAvailability availability = new LlmProviderAvailability(config, key -> null);
    try (ApiServer server = new ApiServer(0, availability, new ObjectMapper());
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();

      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + server.port() + "/api/llm/providers"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
      assertEquals("{\"providers\":[{\"id\":\"OLLAMA\",\"model\":\"qwen3:4b\"}]}", response.body());
    }
  }

  @Test
  void shouldAnswerStatelessChatRequest() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ChatResponse("Hello!", "qwen3:4b", new TokenUsage(12, 4), 25));

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
          "{\"answer\":\"Hello!\",\"model\":\"qwen3:4b\",\"tokenUsage\":{"
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
          "{\"code\":\"INVALID_REQUEST\",\"message\":\"Invalid chat request.\"}", response.body());
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
  void shouldReturnSafeErrorWhenLlmCommunicationFails() throws IOException, InterruptedException {
    ChatService chatService = mock(ChatService.class);
    ChatServiceResolver resolver = mock(ChatServiceResolver.class);
    when(resolver.resolve(LlmProvider.OLLAMA)).thenReturn(chatService);
    when(chatService.ask(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new LlmCommunicationException("Secret provider details"));
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
          "{\"code\":\"LLM_COMMUNICATION_ERROR\",\"message\":"
              + "\"Unable to communicate with the AI model.\"}",
          response.body());
    }
  }

  private HttpRequest chatRequest(ApiServer server, String body) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/chat"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }
}
