package ai.demo.client.ollama.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaRequestTest {

  @Test
  void shouldSerializeAllGenerationOptionsInsideOptionsObject() throws Exception {
    var request =
        new OllamaRequest(
            "qwen3:4b",
            List.of(new OllamaMessage("user", "Hello", null)),
            true,
            new OllamaOptions(0.4, 300, 4096, 1.18));

    JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));

    assertEquals(0.4, json.at("/options/temperature").asDouble());
    assertEquals(300, json.at("/options/num_predict").asInt());
    assertEquals(4096, json.at("/options/num_ctx").asInt());
    assertEquals(1.18, json.at("/options/repeat_penalty").asDouble());
  }
}
