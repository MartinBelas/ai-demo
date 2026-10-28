package ai.demo.client.gemini;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Google Gemini Generate Content API adapter. */
public final class GeminiClient implements LlmClient {

  private static final String PARTS_FIELD = "parts";
  private static final String TEXT_FIELD = "text";
  private static final String ROLE_FIELD = "role";
  private static final String USER_ROLE = "user";
  private static final String MODEL_ROLE = "model";

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
      requireSuccess(response.statusCode(), response.body());
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
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
          requireSuccess(statusCode, readErrorBody(body));
        }
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
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("data:")) {
          String data = line.substring(5).trim();
          if (!data.isEmpty()) {
            JsonNode event = objectMapper.readTree(data);
            emitParts(event, consumer);
            if (event.has("usageMetadata")) tokenUsage = usage(event);
          }
        }
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
                    .path(PARTS_FIELD)
                    .forEach(
                        part -> {
                          String value = part.path(TEXT_FIELD).asText();
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
                    .path(PARTS_FIELD)
                    .forEach(
                        part -> {
                          if (!part.path("thought").asBoolean(false)
                              && !part.path(TEXT_FIELD).asText().isBlank()) {
                            parts.add(part.path(TEXT_FIELD).asText());
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
    List<ChatMessage> messages = prompt.messages();
    int conversationStart = firstNonSystemIndex(messages);
    addSystemInstruction(request, messages.subList(0, conversationStart));
    addContents(contents, messages.subList(conversationStart, messages.size()));
    request
        .putObject("generationConfig")
        .put("temperature", config.generation().temperature())
        .put("maxOutputTokens", config.generation().maxOutputTokens());
    return request;
  }

  private static int firstNonSystemIndex(List<ChatMessage> messages) {
    int index = 0;
    while (index < messages.size() && messages.get(index).role() == Role.SYSTEM) {
      index++;
    }
    return index;
  }

  /**
   * Gemini has a single systemInstruction: every system message before the first conversation
   * message (including RAG sources on the first turn) becomes a part of it.
   */
  private static void addSystemInstruction(ObjectNode request, List<ChatMessage> systemMessages) {
    if (systemMessages.isEmpty()) {
      return;
    }
    ArrayNode parts = request.putObject("systemInstruction").putArray(PARTS_FIELD);
    systemMessages.forEach(message -> parts.addObject().put(TEXT_FIELD, message.content()));
  }

  /**
   * A system message inside the conversation (e.g. RAG sources on a later turn) cannot go into the
   * systemInstruction without moving away from its question, so it is kept in place as the first
   * part of the next user turn. System messages with no user turn after them are appended to the
   * last user turn, or form a user turn of their own when the conversation ends with the model.
   */
  private static void addContents(ArrayNode contents, List<ChatMessage> messages) {
    List<String> pendingSystemTexts = new ArrayList<>();
    for (ChatMessage message : messages) {
      if (message.role() == Role.SYSTEM) {
        pendingSystemTexts.add(message.content());
      } else if (message.role() == Role.ASSISTANT) {
        addContent(contents, MODEL_ROLE, List.of(message.content()));
      } else {
        List<String> texts = new ArrayList<>(pendingSystemTexts);
        pendingSystemTexts.clear();
        texts.add(
            message.role() == Role.TOOL ? "Tool result:\n" + message.content() : message.content());
        addContent(contents, USER_ROLE, texts);
      }
    }
    if (!pendingSystemTexts.isEmpty()) {
      appendToLastUserContent(contents, pendingSystemTexts);
    }
  }

  private static void appendToLastUserContent(ArrayNode contents, List<String> texts) {
    JsonNode last = contents.path(contents.size() - 1);
    if (USER_ROLE.equals(last.path(ROLE_FIELD).asText())) {
      ArrayNode parts = (ArrayNode) last.get(PARTS_FIELD);
      texts.forEach(text -> parts.addObject().put(TEXT_FIELD, text));
    } else {
      addContent(contents, USER_ROLE, texts);
    }
  }

  private static void addContent(ArrayNode contents, String role, List<String> texts) {
    ObjectNode content = contents.addObject().put(ROLE_FIELD, role);
    ArrayNode parts = content.putArray(PARTS_FIELD);
    texts.forEach(text -> parts.addObject().put(TEXT_FIELD, text));
  }

  private void requireSuccess(int statusCode, String body) {
    if (statusCode < 200 || statusCode >= 300) {
      throw new LlmCommunicationException(
          "Gemini returned HTTP status " + statusCode, categorizeError(body, statusCode));
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
        JsonNode error = objectMapper.readTree(body).path("error");
        String status = error.path("status").asText(null);
        if (status != null) {
          return categorize(status, error.path("message").asText(""));
        }
      } catch (JsonProcessingException ignored) {
        // Body isn't a parseable Gemini error payload; fall back to status-based categorization.
      }
    }
    return LlmErrorCategory.fromHttpStatus(statusCode);
  }

  private LlmErrorCategory categorize(String status, String message) {
    String lowerStatus = status.toLowerCase();
    String lowerMessage = message.toLowerCase();
    if (lowerStatus.equals("permission_denied") || lowerStatus.equals("unauthenticated")) {
      return LlmErrorCategory.AUTHENTICATION;
    }
    if (lowerStatus.equals("resource_exhausted")) {
      // Gemini reports both transient per-minute throttling and hard quota/billing exhaustion
      // (e.g. "prepayment credits are depleted") as RESOURCE_EXHAUSTED; the message text is the
      // only way to tell them apart.
      if (lowerMessage.contains("quota")
          || lowerMessage.contains("credit")
          || lowerMessage.contains("billing")
          || lowerMessage.contains("per day")) {
        return LlmErrorCategory.QUOTA_EXHAUSTED;
      }
      return LlmErrorCategory.RATE_LIMIT;
    }
    return LlmErrorCategory.OTHER;
  }
}
