package ai.demo.exception;

/** Exception thrown when communication with an LLM provider fails. */
public class LlmCommunicationException extends LlmException {

  public LlmCommunicationException(String message) {
    super(message);
  }

  public LlmCommunicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
