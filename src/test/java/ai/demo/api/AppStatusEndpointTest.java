package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.TokenUsage;
import ai.demo.config.DemoLimitsConfig;
import ai.demo.config.TestDemoLimits;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.exception.PersistenceException;
import ai.demo.model.chat.ChatResponse;
import ai.demo.persistence.DemoQuotaStore;
import ai.demo.persistence.InMemoryDemoQuotaStore;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class AppStatusEndpointTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final DemoLimitsConfig limits =
      TestDemoLimits.enabled()
          .ipHashSaltEnvironmentVariable("SALT")
          .dailyRequests(200)
          .maxOutputTokensPerCall(1000)
          .build();

  @Test
  void exposesChatOutcomesAndTokensWithoutPromptOrClientIdentifiers() throws Exception {
    var store = new InMemoryDemoQuotaStore(limits);
    var service = mock(ChatService.class);
    // Calls in test order: chat succeeds, chat fails, stream succeeds.
    when(service.ask(any(), any()))
        .thenReturn(new ChatResponse("private answer", "model", new TokenUsage(5, 7), 100))
        .thenThrow(new LlmCommunicationException("provider private details"))
        .thenReturn(new ChatResponse("stream answer", "model", new TokenUsage(2, 3), 100));
    try (var server =
            TestApiServer.create()
                .chatServices(ignored -> service)
                .objectMapper(mapper)
                .demoProtection(new DemoProtection(limits, store, "secret-salt"))
                .build();
        var client = HttpClient.newHttpClient()) {
      server.start();
      assertEquals(200, post(client, server, "/api/chat").statusCode());
      assertEquals(502, post(client, server, "/api/chat").statusCode());
      assertTrue(post(client, server, "/api/chat/stream").body().contains("completion"));
      var response = get(client, server);
      assertEquals(200, response.statusCode());
      var json = mapper.readTree(response.body());
      assertEquals(3, json.path("requests").asInt());
      assertEquals(17, json.path("tokens").asInt());
      assertEquals(2, json.path("completed").asInt());
      assertEquals(1, json.path("failed").asInt());
      assertEquals(0, json.path("activeStreams").asInt());
      assertFalse(response.body().contains("private"));
      assertFalse(response.body().contains("salt"));
      assertEquals(13, json.size());
    }
  }

  @Test
  void storageFailureReturnsSafeUnavailableResponse() throws Exception {
    var store = mock(DemoQuotaStore.class);
    when(store.snapshot(any())).thenThrow(new PersistenceException("secret database", null));
    try (var server =
            TestApiServer.create()
                .objectMapper(mapper)
                .demoProtection(new DemoProtection(limits, store, "secret"))
                .build();
        var client = HttpClient.newHttpClient()) {
      server.start();
      var response = get(client, server);
      assertEquals(503, response.statusCode());
      assertEquals(
          "APP_METRICS_UNAVAILABLE", mapper.readTree(response.body()).path("code").asText());
      assertFalse(response.body().contains("secret"));
    }
  }

  private HttpResponse<String> post(HttpClient client, ApiServer server, String path)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"messages\":[{\"role\":\"USER\",\"content\":\"private prompt\"}]}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(HttpClient client, ApiServer server) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/app/status"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
