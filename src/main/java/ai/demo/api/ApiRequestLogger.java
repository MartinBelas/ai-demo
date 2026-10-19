package ai.demo.api;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one log line per API request: method, path, response status and duration.
 *
 * <p>Only {@code /api/} paths are logged, so static frontend files do not flood the log. Query
 * strings, headers, client IP addresses, and request or response bodies are never logged, because
 * logs must not expose secrets, raw IP addresses, prompts, or responses (see PRD.md).
 */
final class ApiRequestLogger {

  private static final Logger log = LoggerFactory.getLogger(ApiRequestLogger.class);

  // Fixed route prefix of the endpoints registered in ApiServer, not a deployment setting.
  @SuppressWarnings("java:S1075")
  private static final String API_PATH_PREFIX = "/api/";

  private ApiRequestLogger() {}

  static void log(Context context, Number executionTimeMs) {
    String path = context.path();
    if (!path.startsWith(API_PATH_PREFIX)) {
      return;
    }
    log.info(
        "{} {} -> {} ({} ms)",
        context.method(),
        path,
        context.statusCode(),
        Math.round(executionTimeMs.doubleValue()));
  }
}
