package ai.demo.config;

import java.util.Locale;

/** RAG embedding provider, selected independently of the chat {@link LlmProvider}. */
public enum EmbeddingProvider {
  OLLAMA,
  OPENAI,
  GEMINI;

  public static EmbeddingProvider from(String value) {
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported embedding provider: " + value, e);
    }
  }
}
