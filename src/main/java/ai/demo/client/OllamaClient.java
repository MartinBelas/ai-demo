package ai.demo.client;

import ai.demo.config.AppConfig;
import ai.demo.exception.LlmException;
import ai.demo.model.ai.LlmRequest;
import ai.demo.model.ai.LlmResponse;
import ai.demo.model.ai.ollama.OllamaOptions;
import ai.demo.model.ai.ollama.OllamaRequest;
import ai.demo.model.ai.ollama.OllamaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Ollama-specific implementation of LlmClient.
 * Handles communication with Ollama's HTTP API.
 */
public class OllamaClient implements LlmClient {

  private static final String GENERATE_API = "/api/generate";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final AppConfig config;

  /**
   * Creates a new OllamaClient.
   *
   * @param httpClient the HTTP client to use for requests
   * @param objectMapper the object mapper for JSON serialization
   * @param config the application configuration
   */
  public OllamaClient(HttpClient httpClient, ObjectMapper objectMapper, AppConfig config) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.config = config;
  }

  @Override
  public LlmResponse generate(final LlmRequest request) {

    final OllamaRequest ollamaRequest = createRequest(request);

    try {

      final HttpRequest httpRequest = createHttpRequest(ollamaRequest);

      final HttpResponse<String> httpResponse =
              httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
        throw new LlmException(
                "HTTP request failed with status code: " + httpResponse.statusCode() +
                ", body: " + httpResponse.body(),
                null);
      }

      final OllamaResponse ollamaResponse =
              objectMapper.readValue(httpResponse.body(), OllamaResponse.class);

      if (ollamaResponse.response() == null) {
        throw new LlmException("Ollama returned null response", null);
      }

      return new LlmResponse(
              ollamaResponse.response(),
              ollamaResponse.model());

    } catch (InterruptedException e) {

      Thread.currentThread().interrupt();
      throw new LlmException("LLM request interrupted.", e);

    } catch (Exception e) {

      throw new LlmException("Failed to call Ollama.", e);
    }
  }

  private OllamaRequest createRequest(final LlmRequest request) {

    final OllamaOptions options =
            new OllamaOptions(
                    config.numPredict(),
                    config.numCtx(),
                    config.temperature());

    return OllamaRequest.from(
            request,
            config.model(),
            options);
  }

  private HttpRequest createHttpRequest(final OllamaRequest request) throws JsonProcessingException {

    return HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + GENERATE_API))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(request)))
            .build();
  }
}
