package ai.demo.client.gemini;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Role;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Google Gemini Generate Content API adapter. */
public final class GeminiClient implements LlmClient {

  private final AppConfig config;
  private final String apiKey;
  private final HttpTransport transport;
  private final ObjectMapper objectMapper;

  public GeminiClient(
      AppConfig config, String apiKey, HttpTransport transport, ObjectMapper objectMapper) {
    this.config = config;
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("Gemini API key must not be blank");
    }
    this.apiKey = apiKey;
    this.transport = transport;
    this.objectMapper = objectMapper;
  }

  @Override
  public LlmResponse chat(Prompt prompt) {
    try {
      HttpResponse<String> response = transport.send(createRequest(prompt, false));
      requireSuccess(response.statusCode());
      JsonNode body = objectMapper.readTree(response.body());
      return new LlmResponse(text(body), config.gemini().model(), usage(body));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmCommunicationException("Communication with Gemini was interrupted", e);
    } catch (IOException e) {
      throw new LlmCommunicationException("Failed to communicate with Gemini", e);
    }
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {
    try {
      HttpResponse<InputStream> response = transport.sendStreaming(createRequest(prompt, true));
      try (InputStream body = response.body()) {
        requireSuccess(response.statusCode());
        return readStream(body, consumer);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmCommunicationException("Streaming from Gemini was interrupted", e);
    } catch (IOException e) {
      throw new LlmCommunicationException("Failed to stream from Gemini", e);
    }
  }

  private StreamingResult readStream(InputStream body, Consumer<ChatChunk> consumer)
      throws IOException {
    TokenUsage tokenUsage = new TokenUsage(0, 0);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("data:")) continue;
        String data = line.substring(5).trim();
        if (data.isEmpty()) continue;
        JsonNode event = objectMapper.readTree(data);
        emitParts(event, consumer);
        if (event.has("usageMetadata")) tokenUsage = usage(event);
      }
    }
    return new StreamingResult(config.gemini().model(), tokenUsage);
  }

  private void emitParts(JsonNode response, Consumer<ChatChunk> consumer) {
    response
        .path("candidates")
        .forEach(
            candidate ->
                candidate
                    .path("content")
                    .path("parts")
                    .forEach(
                        part -> {
                          String value = part.path("text").asText();
                          if (!value.isBlank()) {
                            ChatChunkType type =
                                part.path("thought").asBoolean(false)
                                    ? ChatChunkType.THINKING
                                    : ChatChunkType.CONTENT;
                            consumer.accept(new ChatChunk(value, type, false));
                          }
                        }));
  }

  private String text(JsonNode response) {
    List<String> parts = new ArrayList<>();
    response
        .path("candidates")
        .forEach(
            candidate ->
                candidate
                    .path("content")
                    .path("parts")
                    .forEach(
                        part -> {
                          if (!part.path("thought").asBoolean(false)
                              && !part.path("text").asText().isBlank()) {
                            parts.add(part.path("text").asText());
                          }
                        }));
    if (parts.isEmpty()) {
      throw new LlmCommunicationException("Gemini response did not contain output text");
    }
    return String.join("", parts);
  }

  private TokenUsage usage(JsonNode response) {
    JsonNode usage = response.path("usageMetadata");
    return new TokenUsage(
        usage.path("promptTokenCount").asInt(0), usage.path("candidatesTokenCount").asInt(0));
  }

  private HttpRequest createRequest(Prompt prompt, boolean streaming)
      throws JsonProcessingException {
    String operation = streaming ? "streamGenerateContent?alt=sse" : "generateContent";
    String uri = config.gemini().baseUrl() + "/models/" + config.gemini().model() + ":" + operation;
    return HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header("x-goog-api-key", apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body(prompt))))
        .build();
  }

  private ObjectNode body(Prompt prompt) {
    ObjectNode request = objectMapper.createObjectNode();
    ArrayNode contents = request.putArray("contents");
    for (ChatMessage message : prompt.messages()) {
      if (message.role() == Role.SYSTEM) {
        request
            .putObject("systemInstruction")
            .putArray("parts")
            .addObject()
            .put("text", message.content());
      } else {
        ObjectNode content = contents.addObject();
        content.put("role", message.role() == Role.ASSISTANT ? "model" : "user");
        String text =
            message.role() == Role.TOOL ? "Tool result:\n" + message.content() : message.content();
        content.putArray("parts").addObject().put("text", text);
      }
    }
    request
        .putObject("generationConfig")
        .put("temperature", config.generation().temperature())
        .put("maxOutputTokens", config.generation().maxOutputTokens());
    return request;
  }

  private void requireSuccess(int statusCode) {
    if (statusCode < 200 || statusCode >= 300) {
      throw new LlmCommunicationException("Gemini returned HTTP status " + statusCode);
    }
  }
}
