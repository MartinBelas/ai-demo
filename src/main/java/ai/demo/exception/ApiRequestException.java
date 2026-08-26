package ai.demo.exception;

/** Indicates that a public API request does not satisfy the application contract. */
public class ApiRequestException extends RuntimeException {

  public ApiRequestException(String message) {
    super(message);
  }

  public ApiRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
