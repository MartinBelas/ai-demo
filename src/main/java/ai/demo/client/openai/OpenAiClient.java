package ai.demo.client.openai;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.openai.dto.OpenAiInputMessage;
import ai.demo.client.openai.dto.OpenAiRequest;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.exception.LlmErrorCategory;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Role;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** OpenAI Responses API adapter. */
public final class OpenAiClient implements LlmClient {

  private static final String RESPONSES_ENDPOINT = "/responses";
  private static final String ERROR_FIELD = "error";

  private final String providerName;
  private final String model;
  private final String baseUrl;
  private final GenerationConfig generation;
  private final String apiKey;
  private final HttpTransport transport;
  private final ObjectMapper objectMapper;

  public OpenAiClient(
      AppConfig config, String apiKey, HttpTransport transport, ObjectMapper objectMapper) {
    this.providerName = "OpenAI";
    this.model = config.openAi().model();
    this.baseUrl = config.openAi().baseUrl();
    this.generation = config.generation();
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("OpenAI API key must not be blank");
    }
    this.apiKey = apiKey;
    this.transport = transport;
    this.objectMapper = objectMapper;
  }

  public OpenAiClient(
      String providerName,
      String model,
      String baseUrl,
      GenerationConfig generation,
      String apiKey,
      HttpTransport transport,
      ObjectMapper objectMapper) {
    this.providerName = providerName;
    this.model = model;
    this.baseUrl = baseUrl;
    this.generation = generation;
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException(providerName + " API key must not be blank");
    }
    this.apiKey = apiKey;
    this.transport = transport;
    this.objectMapper = objectMapper;
  }

  @Override
  public LlmResponse chat(Prompt prompt) {
    try {
      HttpResponse<String> response = transport.send(createRequest(toRequest(prompt, false)));
      requireSuccess(response.statusCode(), response.body());
      return mapResponse(objectMapper.readTree(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmCommunicationException(
          "Communication with " + providerName + " was interrupted", e);
    } catch (IOException e) {
      throw new LlmCommunicationException("Failed to communicate with " + providerName, e);
    }
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {
    try {
      HttpResponse<InputStream> response =
          transport.sendStreaming(createRequest(toRequest(prompt, true)));
      try (InputStream body = response.body()) {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
          requireSuccess(statusCode, readErrorBody(body));
        }
        return readStream(body, consumer);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmCommunicationException("Streaming from " + providerName + " was interrupted", e);
    } catch (IOException e) {
      throw new LlmCommunicationException("Failed to stream from " + providerName, e);
    }
  }

  private StreamingResult readStream(InputStream body, Consumer<ChatChunk> consumer)
      throws IOException {
    String responseModel = model;
    TokenUsage usage = new TokenUsage(0, 0);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("data:")) {
          String data = line.substring(5).trim();
          if (!data.isEmpty() && !"[DONE]".equals(data)) {
            JsonNode event = objectMapper.readTree(data);
            String type = event.path("type").asText();
            if ("response.output_text.delta".equals(type)) {
              emit(event.path("delta").asText(), ChatChunkType.CONTENT, consumer);
            } else if ("response.reasoning_summary_text.delta".equals(type)) {
              emit(event.path("delta").asText(), ChatChunkType.THINKING, consumer);
            } else if ("response.completed".equals(type)) {
              JsonNode completed = event.path("response");
              responseModel = completed.path("model").asText(responseModel);
              usage = mapUsage(completed.path("usage"));
            } else if (ERROR_FIELD.equals(type) || "response.failed".equals(type)) {
              throwStreamError(event);
            }
          }
        }
      }
    }
    return new StreamingResult(responseModel, usage);
  }

  private void throwStreamError(JsonNode event) {
    JsonNode error =
        event.has(ERROR_FIELD) ? event.path(ERROR_FIELD) : event.path("response").path(ERROR_FIELD);
    String code = error.path("code").asText(null);
    String message = error.path("message").asText(null);
    if (message == null) {
      message = event.toString();
    }
    String description = code != null ? code + " - " + message : message;
    throw new LlmCommunicationException(
        providerName + " streaming response failed: " + description, categorize(code));
  }

  private LlmErrorCategory categorize(String errorCode) {
    if (errorCode == null) return LlmErrorCategory.OTHER;
    String lower = errorCode.toLowerCase();
    if (lower.contains("quota") || lower.contains("credit") || lower.contains("billing")) {
      return LlmErrorCategory.QUOTA_EXHAUSTED;
    }
    if (lower.contains("rate_limit")) {
      return LlmErrorCategory.RATE_LIMIT;
    }
    if (lower.contains("api_key") || lower.contains("unauthorized") || lower.contains("permission")) {
      return LlmErrorCategory.AUTHENTICATION;
    }
    return LlmErrorCategory.OTHER;
  }

  private void emit(String value, ChatChunkType type, Consumer<ChatChunk> consumer) {
    if (!value.isBlank()) consumer.accept(new ChatChunk(value, type, false));
  }

  private LlmResponse mapResponse(JsonNode response) {
    List<String> textParts = new ArrayList<>();
    response
        .path("output")
        .forEach(
            item ->
                item.path("content")
                    .forEach(
                        content -> {
                          if ("output_text".equals(content.path("type").asText())) {
                            textParts.add(content.path("text").asText());
                          }
                        }));
    if (textParts.isEmpty()) {
      throw new LlmCommunicationException(providerName + " response did not contain output text");
    }
    return new LlmResponse(
        String.join("", textParts),
        response.path("model").asText(model),
        mapUsage(response.path("usage")));
  }

  private TokenUsage mapUsage(JsonNode usage) {
    return new TokenUsage(
        usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0));
  }

  private HttpRequest createRequest(OpenAiRequest request) throws JsonProcessingException {
    return HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + RESPONSES_ENDPOINT))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
        .build();
  }

  private OpenAiRequest toRequest(Prompt prompt, boolean stream) {
    return new OpenAiRequest(
        model,
        prompt.messages().stream().map(this::toInputMessage).toList(),
        stream,
        generation.temperature(),
        generation.maxOutputTokens(),
        false);
  }

  private OpenAiInputMessage toInputMessage(ChatMessage message) {
    if (message.role() == Role.TOOL) {
      return new OpenAiInputMessage("user", "Tool result:\n" + message.content());
    }
    return new OpenAiInputMessage(message.role().name().toLowerCase(), message.content());
  }

  private void requireSuccess(int statusCode, String body) {
    if (statusCode < 200 || statusCode >= 300) {
      throw new LlmCommunicationException(
          providerName + " returned HTTP status " + statusCode,
          categorizeError(body, statusCode));
    }
  }

  private String readErrorBody(InputStream body) {
    try {
      return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  private LlmErrorCategory categorizeError(String body, int statusCode) {
    if (body != null && !body.isBlank()) {
      try {
        JsonNode error = objectMapper.readTree(body).path(ERROR_FIELD);
        String code = error.path("code").asText(null);
        if (code == null) {
          code = error.path("type").asText(null);
        }
        if (code != null) {
          return categorize(code);
        }
      } catch (JsonProcessingException ignored) {
        // Body isn't a parseable OpenAI error payload; fall back to status-based categorization.
      }
    }
    return categorizeStatus(statusCode);
  }

  private LlmErrorCategory categorizeStatus(int statusCode) {
    if (statusCode == 429) return LlmErrorCategory.RATE_LIMIT;
    if (statusCode == 401 || statusCode == 403) return LlmErrorCategory.AUTHENTICATION;
    if (statusCode == 402) return LlmErrorCategory.QUOTA_EXHAUSTED;
    return LlmErrorCategory.OTHER;
  }
}
