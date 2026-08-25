package ai.demo.exception;

/** Indicates that the embedded HTTP server could not be started or stopped. */
public class ServerException extends RuntimeException {

  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
