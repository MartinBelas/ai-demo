package ai.demo.api;

import ai.demo.exception.ServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

final class ApiResponseWriter {

  static final String JSON_CONTENT_TYPE = "application/json";

  private ApiResponseWriter() {}

  static void write(Context context, int status, Object response, ObjectMapper objectMapper) {
    try {
      context
          .status(status)
          .contentType(JSON_CONTENT_TYPE)
          .result(objectMapper.writeValueAsString(response));
    } catch (JsonProcessingException e) {
      throw new ServerException("Unable to serialize API response", e);
    }
  }

  static void writeError(
      Context context, int status, String code, String message, ObjectMapper objectMapper) {
    write(context, status, ApiErrorResponse.of(code, message), objectMapper);
  }
}
