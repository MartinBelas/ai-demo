package ai.demo.client;

public record TokenUsage(int promptTokens, int completionTokens) {

  public TokenUsage {
    if (promptTokens < 0) {
      throw new IllegalArgumentException("promptTokens must not be negative");
    }

    if (completionTokens < 0) {
      throw new IllegalArgumentException("completionTokens must not be negative");
    }
  }

  public int totalTokens() {
    return promptTokens + completionTokens;
  }
}
