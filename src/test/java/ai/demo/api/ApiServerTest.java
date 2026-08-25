package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.config.OllamaConfig;
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
}
