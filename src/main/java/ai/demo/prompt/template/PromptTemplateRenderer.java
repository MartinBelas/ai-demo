package ai.demo.prompt.template;

import ai.demo.exception.PromptTemplateException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code {{name}}} placeholders in a single pass: substituted values are never scanned
 * again, so a value containing {@code {{tools}}} stays literal. Every placeholder must have a
 * value, otherwise rendering fails instead of sending the raw placeholder to the model.
 */
public class PromptTemplateRenderer {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");

  public String render(PromptTemplate template, Map<String, String> variables) {

    if (template == null) {
      throw new IllegalArgumentException("template must not be null");
    }

    if (variables == null) {
      throw new IllegalArgumentException("variables must not be null");
    }

    Matcher matcher = PLACEHOLDER.matcher(template.text());
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = variables.get(name);
      if (value == null) {
        throw new PromptTemplateException("Prompt template variable '" + name + "' has no value");
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
