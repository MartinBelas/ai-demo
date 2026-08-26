package ai.demo.api;

/** Indicates that an SSE event could not be delivered to the connected client. */
final class SseConnectionException extends RuntimeException {

  SseConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
