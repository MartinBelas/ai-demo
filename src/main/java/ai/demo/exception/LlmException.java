package ai.demo.exception;

/** Base exception for LLM related errors. */
public class LlmException extends RuntimeException {

  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmErrorCategory category() {
    return LlmErrorCategory.OTHER;
  }
}
