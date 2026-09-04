package ai.demo.exception;

/** Exception thrown when communication with an LLM provider fails. */
public class LlmCommunicationException extends LlmException {

  private final LlmErrorCategory category;

  public LlmCommunicationException(String message) {
    this(message, LlmErrorCategory.OTHER);
  }

  public LlmCommunicationException(String message, Throwable cause) {
    this(message, LlmErrorCategory.OTHER, cause);
  }

  public LlmCommunicationException(String message, LlmErrorCategory category) {
    super(message);
    this.category = category;
  }

  public LlmCommunicationException(String message, LlmErrorCategory category, Throwable cause) {
    super(message, cause);
    this.category = category;
  }

  @Override
  public LlmErrorCategory category() {
    return category;
  }
}
