package ai.demo.exception;

/**
 * Exception thrown when the external agent service (ai-agent-lab) fails or returns an unusable
 * response.
 *
 * <p>Deliberately not an {@link LlmException}: agent mode is separate from LLM providers, so its
 * failures get their own user-facing message instead of a provider-specific one.
 */
public class AgentServiceException extends RuntimeException {

  public AgentServiceException(String message) {
    super(message);
  }

  public AgentServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
