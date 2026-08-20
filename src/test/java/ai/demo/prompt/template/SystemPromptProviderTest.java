package ai.demo.prompt.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemPromptProviderTest {

  @Test
  void shouldRenderSystemPromptWithVariables() throws IOException {

    PromptTemplateLoader loader = mock(PromptTemplateLoader.class);
    PromptTemplateRenderer renderer = mock(PromptTemplateRenderer.class);

    PromptTemplate template = mock(PromptTemplate.class);

    when(loader.load(PromptTemplateType.AGENT)).thenReturn(template);

    when(renderer.render(
            template, Map.of("tools", "calculator: Calculates mathematical expressions.")))
        .thenReturn(
            """
                        Available tools:

                        calculator: Calculates mathematical expressions.
                        """);

    SystemPromptProvider provider =
        new SystemPromptProvider(PromptTemplateType.AGENT, loader, renderer);

    String result =
        provider.getSystemPrompt(
            Map.of("tools", "calculator: Calculates mathematical expressions."));

    assertEquals(
        """
                Available tools:

                calculator: Calculates mathematical expressions.
                """,
        result);
  }
}
