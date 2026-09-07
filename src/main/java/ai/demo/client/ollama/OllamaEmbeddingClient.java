package ai.demo.client.ollama;

import ai.demo.client.EmbeddingClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.RagConfig;
import ai.demo.exception.RagException;
import ai.demo.model.rag.Embedding;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/** Uses Ollama's embedding endpoint independently of the selected chat provider. */
public final class OllamaEmbeddingClient implements EmbeddingClient {
  private final HttpTransport transport;
  private final ObjectMapper mapper;
  private final RagConfig config;

  public OllamaEmbeddingClient(HttpTransport transport, ObjectMapper mapper, RagConfig config) {
    this.transport = transport;
    this.mapper = mapper;
    this.config = config;
  }

  @Override
  public Embedding embed(String text) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(stripTrailingSlashes(config.baseUrl()) + "/api/embed"))
              .timeout(Duration.ofSeconds(60))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      mapper.writeValueAsString(new EmbedRequest(config.model(), text, false))))
              .build();
      var response = transport.send(request);
      if (response.statusCode() != 200) {
        throw new RagException("Embedding provider returned HTTP " + response.statusCode());
      }
      EmbedResponse body = mapper.readValue(response.body(), EmbedResponse.class);
      if (body == null || body.embeddings() == null || body.embeddings().size() != 1) {
        throw new RagException("Embedding provider returned an invalid vector count");
      }
      return new Embedding(body.embeddings().getFirst());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RagException("Embedding request interrupted", e);
    } catch (IOException e) {
      throw new RagException("Embedding request failed", e);
    }
  }

  /**
   * Avoids a regex (`/+$`) whose backtracking cost grows faster than linear on pathological input.
   */
  private static String stripTrailingSlashes(String url) {
    int end = url.length();
    while (end > 0 && url.charAt(end - 1) == '/') {
      end--;
    }
    return url.substring(0, end);
  }

  private record EmbedRequest(String model, String input, boolean truncate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EmbedResponse(List<List<Double>> embeddings) {}
}
