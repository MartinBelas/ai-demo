package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiSpecificationTest {

  @Test
  void shouldContainValidApiContract() throws IOException {
    String specification;
    try (var inputStream = getClass().getResourceAsStream("/openapi.yaml")) {
      assertNotNull(inputStream);
      specification = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    SwaggerParseResult result =
        new OpenAPIV3Parser().readContents(specification, null, new ParseOptions());

    assertNotNull(result.getOpenAPI());
    assertTrue(result.getMessages().isEmpty(), () -> String.join(", ", result.getMessages()));
    assertNotNull(result.getOpenAPI().getPaths().get("/api/health").getGet());
    var operation = result.getOpenAPI().getPaths().get("/api/llm/providers").getGet();
    assertNotNull(operation);
    assertEquals("getLlmProviders", operation.getOperationId());
    assertEquals(List.of("LLM Providers"), operation.getTags());
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("LlmProvidersResponse"));
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("LlmProvider"));
  }
}
