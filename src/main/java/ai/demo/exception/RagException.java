package ai.demo.exception;

/** A retrieval/indexing failure; internal details must not be sent to API clients. */
public class RagException extends RuntimeException {
  public RagException(String message) {
    super(message);
  }

  public RagException(String message, Throwable cause) {
    super(message, cause);
  }
}
