package ai.demo.exception;

/** Indicates that a public API request does not satisfy the application contract. */
public class ApiRequestException extends RuntimeException {

  private final String field;

  public ApiRequestException(String field, String message) {
    super(message);
    this.field = field;
  }

  public ApiRequestException(String field, String message, Throwable cause) {
    super(message, cause);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
