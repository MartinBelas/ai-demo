package ai.demo.exception;

public class PromptTemplateException extends RuntimeException {

  public PromptTemplateException(String message) {
    super(message);
  }

  public PromptTemplateException(String message, Throwable cause) {
    super(message, cause);
  }
}
