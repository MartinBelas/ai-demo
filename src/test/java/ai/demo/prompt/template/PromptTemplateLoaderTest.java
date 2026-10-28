package ai.demo.prompt.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PromptTemplateLoaderTest {

  private final PromptTemplateLoader loader = new PromptTemplateLoader();

  @Test
  void shouldLoadAgentTemplate() throws IOException {

    PromptTemplate template = loader.load(PromptTemplateType.AGENT);

    assertTrue(template.text().startsWith("{{systemMessage}}"));
    assertTrue(template.text().contains("{{tools}}"));
  }

  @Test
  void shouldThrowWhenTemplateDoesNotExist() {

    ConfigurationException exception =
        assertThrows(
            ConfigurationException.class, () -> loader.loadFromResource("prompts/missing.md"));

    assertEquals("Prompt template 'prompts/missing.md' not found", exception.getMessage());
  }

  @Test
  void shouldRejectMissingTemplateType() {

    assertThrows(IllegalArgumentException.class, () -> loader.load(null));
  }
}
