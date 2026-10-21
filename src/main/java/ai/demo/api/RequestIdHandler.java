package ai.demo.api;

import ai.demo.logging.RequestId;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assigns the request ID ({@link RequestId}) at the start of every {@code /api/} request and
 * returns it in the {@value RequestId#HEADER} response header. The ID is removed from the MDC only
 * after {@link ApiRequestLogger} has written its line, so that line carries it too.
 */
final class RequestIdHandler {

  private static final Logger log = LoggerFactory.getLogger(RequestIdHandler.class);

  private RequestIdHandler() {}

  static void assign(Context context) {
    String received = context.header(RequestId.HEADER);
    String requestId = RequestId.isValid(received) ? received : RequestId.generate();
    RequestId.set(requestId);
    context.header(RequestId.HEADER, requestId);
    if (received != null && !received.equals(requestId)) {
      // The rejected value itself is not logged: that is exactly what must not reach the log.
      log.warn("Ignoring invalid {} header ({} characters)", RequestId.HEADER, received.length());
    }
  }

  static void logAndClear(Context context, Number executionTimeMs) {
    try {
      ApiRequestLogger.log(context, executionTimeMs);
    } finally {
      RequestId.clear();
    }
  }
}
