package ai.demo.api;

import ai.demo.exception.ServerException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class OpenApiDocument {

  private static final String RESOURCE = "openapi.yaml";

  private OpenApiDocument() {}

  static String load() {
    try (InputStream inputStream =
        OpenApiDocument.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (inputStream == null) {
        throw new ServerException(RESOURCE + " not found", null);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ServerException("Unable to load " + RESOURCE, e);
    }
  }
}
