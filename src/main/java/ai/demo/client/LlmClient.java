package ai.demo.client;

import ai.demo.model.chat.Conversation;

/** Client for communicating with a Large Language Model. */
public interface LlmClient {

  /**
   * Sends a conversation to the LLM and returns its response.
   *
   * @param conversation conversation to process
   * @return generated response
   */
  LlmResponse chat(Conversation conversation);
}
