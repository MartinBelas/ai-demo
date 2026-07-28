package ai.demo.client.ollama;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.ollama.dto.OllamaMessage;
import ai.demo.client.ollama.dto.OllamaRequest;
import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;

public class OllamaClient implements LlmClient {

  private static final String CHAT_ENDPOINT = "/api/chat";

  private final AppConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OllamaClient(
          AppConfig config,
          HttpClient httpClient,
          ObjectMapper objectMapper) {

    this.config = config;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public LlmResponse chat(Conversation conversation) {

    OllamaRequest ollamaRequest =
            new OllamaRequest(
                    config.model(),
                    toMessages(conversation),
                    false
            );

    // TODO implement HTTP call

    throw new UnsupportedOperationException("Not implemented yet.");
  }

  private List<OllamaMessage> toMessages(Conversation conversation) {

    return conversation.messages()
            .stream()
            .map(this::toOllamaMessage)
            .toList();
  }

  private OllamaMessage toOllamaMessage(ChatMessage message) {

    return new OllamaMessage(
            mapToOllamaRole(message.role()),
            message.content()
    );
  }

  private String mapToOllamaRole(Role role) {

    return switch (role) {
      case USER -> "user";
      case ASSISTANT -> "assistant";
      case SYSTEM -> "system";
    };
  }
}