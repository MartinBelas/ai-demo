package ai.demo.prompt.template;

public enum PromptTemplateType {
  CHAT("chat.md"),
  AGENT("agent.md");

  private final String templateName;

  PromptTemplateType(String templateName) {
    this.templateName = templateName;
  }

  public String getTemplateName() {
    return templateName;
  }
}
