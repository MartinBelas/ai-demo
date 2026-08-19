package ai.demo.prompt.template;

import ai.demo.exception.PromptTemplateException;
import java.io.IOException;
import java.util.Map;

public class SystemPromptProvider {

  private final PromptTemplateRenderer renderer;
  private final PromptTemplate template;

  public SystemPromptProvider(
      PromptTemplateType templateType,
      PromptTemplateLoader templateLoader,
      PromptTemplateRenderer renderer) {

    this.renderer = renderer;

    try {
      this.template = templateLoader.load(templateType);
    } catch (IOException e) {
      throw new PromptTemplateException("Failed to load prompt template: " + templateType, e);
    }
  }

  public String getSystemPrompt() {
    return getSystemPrompt(Map.of());
  }

  public String getSystemPrompt(Map<String, String> variables) {
    return renderer.render(template, variables);
  }
}
