package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.demo.agent.RemoteAgent;
import ai.demo.client.agentlab.AgentLabClient;
import ai.demo.client.http.JdkHttpTransport;
import ai.demo.config.AgentLabConfig;
import ai.demo.config.LlmProvider;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Agent mode through the real HTTP stack: ApiServer → ChatService → RemoteAgent → AgentLabClient →
 * HTTP → a fake ai-agent-lab service. Only the agent service itself is faked.
 */
class AgentModeEndToEndTest {

  private static final String LOCAL_SERVER = "http://localhost:";
  private static final String LAB_REPLY =
      """
      {
        "reply": "1 EUR is 24.30 CZK today.",
        "sources": [{"title": "CNB rates", "url": "https://www.cnb.cz/rates"}],
        "usage": {"model_calls": 2, "tool_calls": 1, "web_searches": 1,
                  "input_tokens": 900, "output_tokens": 120}
      }
      """;
  private static final String AGENT_REQUEST =
      """
      {"provider": "OLLAMA", "agent": true, "messages": [
        {"role": "USER", "content": "EUR to CZK?"},
        {"role": "ASSISTANT", "content": "About 24 CZK."},
        {"role": "USER", "content": "And today exactly?"}
      ]}
      """;

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> labBodies = new CopyOnWriteArrayList<>();
  private final List<String> labRequestIds = new CopyOnWriteArrayList<>();
  private volatile int labStatus = 200;
  private volatile String labResponse = LAB_REPLY;
  private Javalin fakeLab;
  private HttpClient httpClient;
  private ChatServiceResolver providerServices;

  @BeforeEach
  void startFakeLab() {
    fakeLab =
        Javalin.create(
                config ->
                    config.routes.post(
                        "/api/chat",
                        context -> {
                          labBodies.add(context.body());
                          labRequestIds.add(context.header("X-Request-ID"));
                          context
                              .status(labStatus)
                              .contentType("application/json")
                              .result(labResponse);
                        }))
            .start(0);
    httpClient = HttpClient.newHttpClient();
    providerServices = mock(ChatServiceResolver.class);
  }

  @AfterEach
  void stopFakeLab() {
    httpClient.close();
    fakeLab.stop();
  }

  @Test
  void shouldAnswerChatRequestThroughTheAgentService() throws Exception {
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response = post(server, "/api/chat", AGENT_REQUEST);

      assertEquals(200, response.statusCode());
      JsonNode body = mapper.readTree(response.body());
      assertEquals("1 EUR is 24.30 CZK today.", body.path("answer").asText());
      assertEquals("agent-lab", body.path("model").asText());
      assertEquals(1020, body.path("tokenUsage").path("totalTokens").asInt());
      assertEquals("CNB rates", body.path("webSources").path(0).path("title").asText());
      assertEquals(
          "https://www.cnb.cz/rates", body.path("webSources").path(0).path("url").asText());
      assertTrue(body.path("sources").isMissingNode());

      JsonNode labRequest = mapper.readTree(labBodies.getFirst());
      assertEquals("And today exactly?", labRequest.path("message").asText());
      assertEquals(2, labRequest.path("history").size());
      assertEquals("assistant", labRequest.path("history").path(1).path("role").asText());
      assertNotNull(labRequestIds.getFirst());
      verifyNoInteractions(providerServices);
    }
  }

  @Test
  void shouldStreamAgentAnswerAndWebSources() throws Exception {
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response = post(server, "/api/chat/stream", AGENT_REQUEST);

      assertEquals(200, response.statusCode());
      String body = response.body();
      int content =
          body.indexOf("event: content\ndata: {\"content\":\"1 EUR is 24.30 CZK today.\"}");
      int completion = body.indexOf("event: completion\ndata: {\"model\":\"agent-lab\"");
      assertTrue(content >= 0, body);
      assertTrue(completion > content, body);
      assertTrue(body.contains("\"webSources\":[{\"title\":\"CNB rates\""), body);
    }
  }

  @Test
  void shouldReportAgentServiceFailureWithoutItsDetails() throws Exception {
    labStatus = 500;
    labResponse = "{\"detail\": \"Traceback: secret internals\"}";
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response = post(server, "/api/chat", AGENT_REQUEST);

      assertEquals(502, response.statusCode());
      assertEquals(
          AgentModeErrors.UNAVAILABLE_CODE, mapper.readTree(response.body()).path("code").asText());
      assertFalse(response.body().contains("secret"));
    }
  }

  @Test
  void shouldStreamTerminalErrorWhenAgentServiceFails() throws Exception {
    labStatus = 502;
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response = post(server, "/api/chat/stream", AGENT_REQUEST);

      assertTrue(response.body().contains("event: error"), response.body());
      assertTrue(response.body().contains(AgentModeErrors.UNAVAILABLE_CODE), response.body());
    }
  }

  @Test
  void shouldRejectAgentRequestWhenAgentModeIsDisabled() throws Exception {
    try (ApiServer server = server(null)) {
      server.start();

      HttpResponse<String> response = post(server, "/api/chat", AGENT_REQUEST);

      assertEquals(400, response.statusCode());
      assertEquals(
          AgentModeErrors.DISABLED_CODE, mapper.readTree(response.body()).path("code").asText());
      assertTrue(labBodies.isEmpty());
    }
  }

  @Test
  void shouldRejectAgentModeCombinedWithRag() throws Exception {
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response =
          post(
              server,
              "/api/chat",
              """
              {"agent": true, "rag": true, "messages": [{"role": "USER", "content": "Hi"}]}
              """);

      assertEquals(400, response.statusCode());
      assertEquals(
          "rag", mapper.readTree(response.body()).path("details").path(0).path("field").asText());
      assertTrue(labBodies.isEmpty());
    }
  }

  @Test
  void shouldRejectTooLongAgentMessageBeforeCallingTheService() throws Exception {
    String message = "x".repeat(RemoteAgent.MAX_MESSAGE_CHARACTERS + 1);
    try (ApiServer server = server(agentChatService())) {
      server.start();

      HttpResponse<String> response =
          post(
              server,
              "/api/chat",
              "{\"agent\": true, \"messages\": [{\"role\": \"USER\", \"content\": \""
                  + message
                  + "\"}]}");

      assertEquals(400, response.statusCode());
      assertEquals(
          "messages[0].content",
          mapper.readTree(response.body()).path("details").path(0).path("field").asText());
      assertTrue(labBodies.isEmpty());
    }
  }

  @Test
  void shouldReportAgentModeStatus() throws Exception {
    try (ApiServer enabled = server(agentChatService());
        ApiServer disabled = server(null)) {
      enabled.start();
      disabled.start();

      assertEquals("{\"enabled\":true}", agentStatus(enabled).body());
      assertEquals("{\"enabled\":false}", agentStatus(disabled).body());
    }
  }

  private ChatService agentChatService() {
    AgentLabConfig config =
        new AgentLabConfig(true, "http://localhost:" + fakeLab.port(), Duration.ofSeconds(5));
    return new ChatService(
        new RemoteAgent(new AgentLabClient(config, new JdkHttpTransport(httpClient), mapper)));
  }

  private ApiServer server(ChatService agentChatService) {
    return new ApiServer(
        0,
        null,
        new ChatServiceSelector(providerServices, agentChatService),
        LlmProvider.OLLAMA,
        mapper,
        DemoProtection.localDevelopment(),
        false);
  }

  private HttpResponse<String> post(ApiServer server, String path, String body)
      throws IOException, InterruptedException {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> agentStatus(ApiServer server)
      throws IOException, InterruptedException {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create(LOCAL_SERVER + server.port() + "/api/agent/status"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
