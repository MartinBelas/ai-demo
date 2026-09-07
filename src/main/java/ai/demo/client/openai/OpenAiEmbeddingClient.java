package ai.demo.client.openai;

import ai.demo.client.EmbeddingClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.RagConfig;
import ai.demo.exception.RagException;
import ai.demo.model.rag.Embedding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** Uses OpenAI's embeddings endpoint independently of the selected chat provider. */
public final class OpenAiEmbeddingClient implements EmbeddingClient {

  private static final String ENDPOINT = "/embeddings";

  private final RagConfig config;
  private final String apiKey;
  private final HttpTransport transport;
  private final ObjectMapper mapper;

  public OpenAiEmbeddingClient(
      RagConfig config, String apiKey, HttpTransport transport, ObjectMapper mapper) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("OpenAI embedding API key must not be blank");
    }
    this.config = config;
    this.apiKey = apiKey;
    this.transport = transport;
    this.mapper = mapper;
  }

  @Override
  public Embedding embed(String text) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(config.baseUrl() + ENDPOINT))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      mapper.writeValueAsString(new EmbedRequest(config.model(), text))))
              .build();
      HttpResponse<String> response = transport.send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RagException("OpenAI embedding request returned HTTP " + response.statusCode());
      }
      JsonNode vector = mapper.readTree(response.body()).path("data").path(0).path("embedding");
      if (!vector.isArray() || vector.isEmpty()) {
        throw new RagException("OpenAI embedding response did not contain a vector");
      }
      return new Embedding(values(vector));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RagException("OpenAI embedding request was interrupted", e);
    } catch (IOException e) {
      throw new RagException("Failed to communicate with OpenAI for embeddings", e);
    }
  }

  private List<Double> values(JsonNode vector) {
    List<Double> values = new ArrayList<>();
    vector.forEach(node -> values.add(node.asDouble()));
    return values;
  }

  private record EmbedRequest(String model, String input) {}
}
