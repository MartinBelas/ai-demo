package ai.demo.logging;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The ID of the API request being handled, kept in the SLF4J MDC so that every log line written
 * while the request runs carries it (see {@code logback.xml}).
 *
 * <p>The ID is assigned once per {@code /api/} request: taken from the caller's {@value #HEADER}
 * header when it is valid, otherwise generated. It is returned in the response header and sent to
 * the ai-agent-lab service, which logs the same ID, so one request can be traced across both
 * services. The MDC is thread-bound; chat requests, including SSE streaming, run on the request
 * thread.
 */
public final class RequestId {

  public static final String HEADER = "X-Request-ID";
  public static final String MDC_KEY = "requestId";

  // The header comes from the caller and ends up in log lines, so only a short, harmless set of
  // characters is accepted: no spaces, line breaks or control characters (log injection).
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final int GENERATED_LENGTH = 8;

  private RequestId() {}

  /** A new short ID, e.g. {@code 8ff9efad}. Not a secret, only a log correlation key. */
  public static String generate() {
    return UUID.randomUUID().toString().substring(0, GENERATED_LENGTH);
  }

  public static boolean isValid(String value) {
    return value != null && VALID.matcher(value).matches();
  }

  /** The ID of the request handled on this thread, if any. */
  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }

  /** The current request ID, or a new one for work outside an API request (e.g. the console). */
  public static String currentOrGenerate() {
    return current().orElseGet(RequestId::generate);
  }

  public static void set(String requestId) {
    MDC.put(MDC_KEY, requestId);
  }

  public static void clear() {
    MDC.remove(MDC_KEY);
  }
}
