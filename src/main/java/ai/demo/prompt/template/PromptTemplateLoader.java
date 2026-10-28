package ai.demo.prompt.template;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PromptTemplateLoader {

  private static final String TEMPLATE_DIRECTORY = "prompts/";

  public PromptTemplate load(PromptTemplateType templateType) throws IOException {

    if (templateType == null) {
      throw new IllegalArgumentException("templateType must not be null");
    }

    return loadFromResource(TEMPLATE_DIRECTORY + templateType.getTemplateName());
  }

  PromptTemplate loadFromResource(String resourceName) throws IOException {

    try (InputStream inputStream =
        PromptTemplateLoader.class.getClassLoader().getResourceAsStream(resourceName)) {

      if (inputStream == null) {
        throw new ConfigurationException("Prompt template '" + resourceName + "' not found");
      }

      String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

      return new PromptTemplate(template);
    }
  }
}
