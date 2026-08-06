package ai.demo.prompt.template;

import java.util.Map;

public class PromptTemplateRenderer {

  public String render(PromptTemplate template) {
    return render(template, Map.of());
  }

  public String render(PromptTemplate template, Map<String, String> variables) {

    if (template == null) {
      throw new IllegalArgumentException("template must not be null");
    }

    if (variables == null) {
      throw new IllegalArgumentException("variables must not be null");
    }

    String result = template.text();

    for (Map.Entry<String, String> variable : variables.entrySet()) {
      result = result.replace("{{" + variable.getKey() + "}}", variable.getValue());
    }

    return result;
  }
}
