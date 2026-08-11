package ai.demo.client;

import ai.demo.model.chat.ChatChunk;
import ai.demo.model.prompt.Prompt;
import java.util.function.Consumer;

/** Client for communicating with a Large Language Model. */
public interface LlmClient {

  /**
   * Sends a prompt to the LLM and returns its response.
   *
   * @param prompt prompt to process
   * @return generated response
   */
  LlmResponse chat(Prompt prompt);

  StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer);
}
