package ai.demo.client.ollama;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.ollama.dto.OllamaMessage;
import ai.demo.client.ollama.dto.OllamaRequest;
import ai.demo.client.ollama.dto.OllamaResponse;
import ai.demo.config.AppConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Role;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;

public class OllamaClient implements LlmClient {

  private static final String CHAT_ENDPOINT = "/api/chat";

  private final AppConfig config;
  private final HttpTransport httpTransport;
  private final ObjectMapper objectMapper;

  public OllamaClient(AppConfig config, HttpTransport httpTransport, ObjectMapper objectMapper) {
    this.config = config;
    this.httpTransport = httpTransport;
    this.objectMapper = objectMapper;
  }

  @Override
  public LlmResponse chat(Prompt prompt) {
    OllamaRequest ollamaRequest = toOllamaRequest(prompt);
    OllamaResponse ollamaResponse = send(ollamaRequest);
    return toLlmResponse(ollamaResponse);
  }

  @Override
  public void stream(Prompt prompt, Consumer<ChatChunk> consumer) {

    OllamaRequest request = toStreamingOllamaRequest(prompt);

    try {

      HttpResponse<InputStream> response = httpTransport.sendStreaming(createHttpRequest(request));

      if (response.statusCode() != 200) {
        throw new LlmCommunicationException("Ollama returned HTTP status " + response.statusCode());
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {

        String line;

        while ((line = reader.readLine()) != null) {

          if (line.isBlank()) {
            continue;
          }

          OllamaResponse chunk = objectMapper.readValue(line, OllamaResponse.class);

          consumer.accept(new ChatChunk(chunk.message().content(), chunk.done()));
        }
      }

    } catch (InterruptedException e) {

      Thread.currentThread().interrupt();

      throw new LlmCommunicationException("Streaming from Ollama was interrupted", e);

    } catch (IOException e) {

      throw new LlmCommunicationException("Failed to stream from Ollama", e);
    }
  }

  private OllamaResponse send(OllamaRequest ollamaRequest) {

    try {
      HttpRequest httpRequest = createHttpRequest(ollamaRequest);

      HttpResponse<String> httpResponse = httpTransport.send(httpRequest);

      if (httpResponse.statusCode() != 200) {
        throw new LlmCommunicationException(
            "Ollama returned HTTP status " + httpResponse.statusCode());
      }

      return objectMapper.readValue(httpResponse.body(), OllamaResponse.class);

    } catch (IOException e) {
      throw new LlmCommunicationException("Failed to communicate with Ollama", e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      throw new LlmCommunicationException("Communication with Ollama was interrupted", e);
    }
  }

  private HttpRequest createHttpRequest(OllamaRequest request) throws JsonProcessingException {

    return HttpRequest.newBuilder()
        .uri(chatUri())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
        .build();
  }

  private URI chatUri() {
    return URI.create(config.baseUrl() + CHAT_ENDPOINT);
  }

  private OllamaRequest toOllamaRequest(Prompt prompt) {
    return new OllamaRequest(config.model(), toMessages(prompt), false);
  }

  private OllamaRequest toStreamingOllamaRequest(Prompt prompt) {
    return new OllamaRequest(config.model(), toMessages(prompt), true);
  }

  private LlmResponse toLlmResponse(OllamaResponse ollamaResponse) {
    return new LlmResponse(ollamaResponse.message().content(), ollamaResponse.model());
  }

  private List<OllamaMessage> toMessages(Prompt prompt) {
    return prompt.messages().stream().map(this::toOllamaMessage).toList();
  }

  private OllamaMessage toOllamaMessage(ChatMessage chatMessage) {
    return new OllamaMessage(mapToOllamaRole(chatMessage.role()), chatMessage.content());
  }

  private String mapToOllamaRole(Role role) {
    return switch (role) {
      case USER -> "user";
      case ASSISTANT -> "assistant";
      case SYSTEM -> "system";
    };
  }
}
