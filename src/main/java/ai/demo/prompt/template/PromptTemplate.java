package ai.demo.prompt.template;

public record PromptTemplate(String text) {

  public PromptTemplate {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Template text must not be blank");
    }
  }
}
