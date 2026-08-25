package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
}
