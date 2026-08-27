package ai.demo.api;

import ai.demo.exception.ServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class SseEventWriter {

  private static final byte[] EVENT_SEPARATOR = "\n\n".getBytes(StandardCharsets.UTF_8);

  private final OutputStream outputStream;
  private final HttpServletResponse response;
  private final ObjectMapper objectMapper;

  SseEventWriter(
      HttpServletResponse response, OutputStream outputStream, ObjectMapper objectMapper) {
    this.response = response;
    this.outputStream = outputStream;
    this.objectMapper = objectMapper;
  }

  void send(String eventName, Object data) {
    try {
      write("event: " + eventName + "\n");
      write("data: " + objectMapper.writeValueAsString(data));
      outputStream.write(EVENT_SEPARATOR);
      response.flushBuffer();
    } catch (JsonProcessingException e) {
      throw new ServerException("Unable to serialize SSE event", e);
    } catch (IOException e) {
      throw new SseConnectionException("Unable to send SSE event", e);
    }
  }

  private void write(String value) throws IOException {
    outputStream.write(value.getBytes(StandardCharsets.UTF_8));
  }
}
