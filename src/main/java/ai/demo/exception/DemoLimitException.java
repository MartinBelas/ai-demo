package ai.demo.exception;

/** Public demo quota rejection without exposing the internal limit that was reached. */
public final class DemoLimitException extends RuntimeException {

  private final boolean unavailable;

  public DemoLimitException(boolean unavailable, Throwable cause) {
    super(unavailable ? "Demo quota service is unavailable" : "Demo quota exceeded", cause);
    this.unavailable = unavailable;
  }

  public DemoLimitException() {
    this(false, null);
  }

  public boolean unavailable() {
    return unavailable;
  }
}
