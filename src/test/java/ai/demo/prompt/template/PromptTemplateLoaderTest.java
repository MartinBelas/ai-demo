package ai.demo.prompt.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PromptTemplateLoaderTest {

  private final PromptTemplateLoader loader = new PromptTemplateLoader();

  @Test
  void shouldLoadTemplate() throws IOException {

    PromptTemplate template = loader.load(PromptTemplateType.CHAT);

    String expected =
        """
                You are a helpful assistant.

                {{conversation}}
                """;

    assertEquals(expected.trim(), template.text().trim());
  }

  @Test
  void shouldThrowWhenTemplateDoesNotExist() {

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> loader.load(null));

    assertEquals("Prompt template 'prompts/missing.md' not found", exception.getMessage());
  }
}
