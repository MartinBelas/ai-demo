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
    SwaggerParseResult result = parseSpecification();

    assertNotNull(result.getOpenAPI());
    assertTrue(result.getMessages().isEmpty(), () -> String.join(", ", result.getMessages()));
    assertNotNull(result.getOpenAPI().getPaths().get("/api/health").getGet());
    assertNotNull(result.getOpenAPI().getPaths().get("/api/app/status").getGet());
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("AppStatus"));
    var operation = result.getOpenAPI().getPaths().get("/api/llm/providers").getGet();
    assertNotNull(operation);
    assertEquals("getLlmProviders", operation.getOperationId());
    assertEquals(List.of("LLM Providers"), operation.getTags());
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("LlmProvidersResponse"));
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("LlmProvider"));
    var chatOperation = result.getOpenAPI().getPaths().get("/api/chat").getPost();
    assertNotNull(chatOperation);
    assertEquals("createChatResponse", chatOperation.getOperationId());
    assertEquals(List.of("Chat"), chatOperation.getTags());
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("ChatRequest"));
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("ChatResponse"));
    var streamingOperation = result.getOpenAPI().getPaths().get("/api/chat/stream").getPost();
    assertNotNull(streamingOperation);
    assertEquals("streamChatResponse", streamingOperation.getOperationId());
    assertEquals(List.of("Chat"), streamingOperation.getTags());
    assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("SseCompletionEvent"));
  }

  @Test
  void shouldDescribeAgentMode() throws IOException {
    SwaggerParseResult result = parseSpecification();

    var agentStatusOperation = result.getOpenAPI().getPaths().get("/api/agent/status").getGet();
    assertNotNull(agentStatusOperation);
    assertEquals("getAgentStatus", agentStatusOperation.getOperationId());
    var schemas = result.getOpenAPI().getComponents().getSchemas();
    assertNotNull(schemas.get("WebSource"));
    assertNotNull(schemas.get("ChatRequest").getProperties().get("agent"));
    assertNotNull(schemas.get("ChatResponse").getProperties().get("webSources"));
    assertNotNull(schemas.get("SseCompletionEvent").getProperties().get("webSources"));
  }

  @Test
  void shouldDocumentInvalidModelResponseErrorForChatAndStreaming() throws IOException {
    SwaggerParseResult result = parseSpecification();

    var chatExamples =
        result
            .getOpenAPI()
            .getPaths()
            .get("/api/chat")
            .getPost()
            .getResponses()
            .get("502")
            .getContent()
            .get("application/json")
            .getExamples();
    assertTrue(chatExamples.containsKey("invalidResponse"));
    assertTrue(
        chatExamples.get("invalidResponse").getValue().toString().contains("LLM_INVALID_RESPONSE"));

    var streamExamples =
        result
            .getOpenAPI()
            .getPaths()
            .get("/api/chat/stream")
            .getPost()
            .getResponses()
            .get("200")
            .getContent()
            .get("text/event-stream")
            .getExamples();
    assertTrue(streamExamples.containsKey("invalidResponse"));
    assertTrue(
        streamExamples
            .get("invalidResponse")
            .getValue()
            .toString()
            .contains("LLM_INVALID_RESPONSE"));
  }

  private SwaggerParseResult parseSpecification() throws IOException {
    String specification;
    try (var inputStream = getClass().getResourceAsStream("/openapi.yaml")) {
      assertNotNull(inputStream);
      specification = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    return new OpenAPIV3Parser().readContents(specification, null, new ParseOptions());
  }
}
