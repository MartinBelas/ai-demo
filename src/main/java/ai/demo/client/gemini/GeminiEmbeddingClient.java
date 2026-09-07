package ai.demo.client.gemini;

import ai.demo.client.EmbeddingClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.RagConfig;
import ai.demo.exception.RagException;
import ai.demo.model.rag.Embedding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** Uses Gemini's embedContent endpoint independently of the selected chat provider. */
public final class GeminiEmbeddingClient implements EmbeddingClient {

  private final RagConfig config;
  private final String apiKey;
  private final HttpTransport transport;
  private final ObjectMapper mapper;

  public GeminiEmbeddingClient(
      RagConfig config, String apiKey, HttpTransport transport, ObjectMapper mapper) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("Gemini embedding API key must not be blank");
    }
    this.config = config;
    this.apiKey = apiKey;
    this.transport = transport;
    this.mapper = mapper;
  }

  @Override
  public Embedding embed(String text) {
    try {
      ObjectNode body = mapper.createObjectNode();
      body.putObject("content").putArray("parts").addObject().put("text", text);
      String uri = config.baseUrl() + "/models/" + config.model() + ":embedContent";
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(uri))
              .header("x-goog-api-key", apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = transport.send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RagException("Gemini embedding request returned HTTP " + response.statusCode());
      }
      JsonNode vector = mapper.readTree(response.body()).path("embedding").path("values");
      if (!vector.isArray() || vector.isEmpty()) {
        throw new RagException("Gemini embedding response did not contain a vector");
      }
      return new Embedding(values(vector));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RagException("Gemini embedding request was interrupted", e);
    } catch (IOException e) {
      throw new RagException("Failed to communicate with Gemini for embeddings", e);
    }
  }

  private List<Double> values(JsonNode vector) {
    List<Double> values = new ArrayList<>();
    vector.forEach(node -> values.add(node.asDouble()));
    return values;
  }
}
