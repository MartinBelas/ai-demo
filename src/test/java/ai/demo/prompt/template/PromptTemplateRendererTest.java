package ai.demo.prompt.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.PromptTemplateException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptTemplateRendererTest {

  private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

  @Test
  void shouldReplaceEveryPlaceholder() {
    PromptTemplate template = new PromptTemplate("{{systemMessage}}\n\nTools:\n{{tools}}");

    String result =
        renderer.render(template, Map.of("systemMessage", "Be helpful.", "tools", "calculator"));

    assertEquals("Be helpful.\n\nTools:\ncalculator", result);
  }

  @Test
  void shouldNotExpandPlaceholdersInsideVariableValues() {
    PromptTemplate template = new PromptTemplate("{{systemMessage}} | {{tools}}");

    String result =
        renderer.render(
            template, Map.of("systemMessage", "Say {{tools}} literally.", "tools", "calculator"));

    assertEquals("Say {{tools}} literally. | calculator", result);
  }

  @Test
  void shouldKeepRegexSpecialCharactersInValues() {
    PromptTemplate template = new PromptTemplate("Cost: {{price}}");

    assertEquals("Cost: $5 \\o/", renderer.render(template, Map.of("price", "$5 \\o/")));
  }

  @Test
  void shouldRejectPlaceholderWithoutValue() {
    PromptTemplate template = new PromptTemplate("Available tools: {{tools}}");
    Map<String, String> variables = Map.of();

    PromptTemplateException exception =
        assertThrows(PromptTemplateException.class, () -> renderer.render(template, variables));

    assertEquals("Prompt template variable 'tools' has no value", exception.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{{ tools }}", "{{tool-list}}", "{{}}"})
  void shouldRejectMalformedPlaceholder(String text) {
    PromptTemplate template = new PromptTemplate("Available tools: " + text);
    Map<String, String> variables = Map.of("tools", "calculator");

    assertThrows(PromptTemplateException.class, () -> renderer.render(template, variables));
  }

  @Test
  void shouldIgnoreUnusedVariables() {
    PromptTemplate template = new PromptTemplate("Hello");

    assertEquals("Hello", renderer.render(template, Map.of("tools", "calculator")));
  }
}
