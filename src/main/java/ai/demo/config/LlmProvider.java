package ai.demo.config;

import java.util.Locale;

public enum LlmProvider {
  OLLAMA,
  OPENAI,
  GROQ,
  GEMINI;

  public static LlmProvider from(String value) {
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported LLM provider: " + value, e);
    }
  }
}
