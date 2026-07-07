package ai.demo.client;

import ai.demo.model.ai.LlmRequest;
import ai.demo.model.ai.LlmResponse;

/**
 * Interface for LLM (Large Language Model) clients.
 * Defines the contract for generating responses from LLM providers.
 */
public interface LlmClient {

  /**
   * Generates a response from the LLM for the given request.
   *
   * @param request the LLM request containing the prompt
   * @return the LLM response containing the generated text and model info
   */
  LlmResponse generate(LlmRequest request);
}
