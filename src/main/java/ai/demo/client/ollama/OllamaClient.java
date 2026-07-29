package ai.demo.client.ollama;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.ollama.dto.OllamaMessage;
import ai.demo.client.ollama.dto.OllamaRequest;
import ai.demo.client.ollama.dto.OllamaResponse;
import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class OllamaClient implements LlmClient {

  private static final String CHAT_ENDPOINT = "/api/chat";

  private final AppConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OllamaClient(AppConfig config, HttpClient httpClient, ObjectMapper objectMapper) {

    this.config = config;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public LlmResponse chat(Conversation conversation) {
    OllamaRequest ollamaRequest = toOllamaRequest(conversation);
    OllamaResponse ollamaResponse = send(ollamaRequest);
    return toLlmResponse(ollamaResponse);
  }

  private OllamaResponse send(OllamaRequest ollamaRequest) {

    try {

      HttpRequest httpRequest = createHttpRequest(ollamaRequest);

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (httpResponse.statusCode() != 200) {
        throw new RuntimeException("Ollama returned HTTP " + httpResponse.statusCode());
      }

      OllamaResponse ollamaResponse =
              objectMapper.readValue(httpResponse.body(), OllamaResponse.class);

      return ollamaResponse;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to call Ollama", e);
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

  private OllamaRequest toOllamaRequest(Conversation conversation) {
    return new OllamaRequest(config.model(), toMessages(conversation), false);
  }

  private LlmResponse toLlmResponse(OllamaResponse ollamaResponse) {
    return new LlmResponse(ollamaResponse.message().content(), ollamaResponse.model());
  }

  private List<OllamaMessage> toMessages(Conversation conversation) {
    return conversation.messages().stream()
            .map(this::toOllamaMessage)
            .toList();
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
