package ai.demo.client.groq;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.openai.OpenAiClient;
import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.prompt.Prompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;

/** GroqCloud adapter backed by its OpenAI-compatible Responses API. */
public final class GroqClient implements LlmClient {

  private final LlmClient delegate;

  public GroqClient(
      AppConfig config, String apiKey, HttpTransport transport, ObjectMapper objectMapper) {
    this.delegate =
        new OpenAiClient(
            "Groq",
            config.groq().model(),
            config.groq().baseUrl(),
            config.generation(),
            apiKey,
            transport,
            objectMapper);
  }

  @Override
  public LlmResponse chat(Prompt prompt) {
    return delegate.chat(prompt);
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {
    return delegate.stream(prompt, consumer);
  }
}
