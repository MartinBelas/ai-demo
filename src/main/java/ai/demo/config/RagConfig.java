package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.net.URI;

/**
 * RAG limits apply even when public chat quotas are disabled. All sizes are UTF-8 bytes.
 *
 * <p>The embedding provider is independent of the currently selected chat {@link LlmProvider}.
 */
public record RagConfig(
    boolean enabled,
    EmbeddingProvider provider,
    String baseUrl,
    String model,
    String apiKeyEnvironmentVariable,
    int maxDocumentBytes,
    int maxDocuments,
    int maxCorpusBytes,
    int maxChunkBytes,
    int maxContextBytes,
    int maxQueryBytes,
    int maxChunks,
    double minimumScore) {
  public RagConfig {
    if (provider == null) throw new ConfigurationException("RAG embedding provider is required");
    if (model == null || model.isBlank()) throw new ConfigurationException("RAG model is required");
    if (provider != EmbeddingProvider.OLLAMA
        && (apiKeyEnvironmentVariable == null || apiKeyEnvironmentVariable.isBlank())) {
      throw new ConfigurationException(
          "RAG embedding API key environment variable is required for provider " + provider);
    }
    if (!isValidBaseUrl(baseUrl)) {
      throw new ConfigurationException("Invalid RAG embedding base URL");
    }
    if (maxDocumentBytes < 1
        || maxDocuments < 1
        || maxCorpusBytes < 1
        || maxChunkBytes < 4
        || maxContextBytes < maxChunkBytes
        || maxQueryBytes < 1
        || maxChunks < 1
        || !Double.isFinite(minimumScore)
        || minimumScore < -1
        || minimumScore > 1) {
      throw new ConfigurationException("Invalid RAG size or similarity limits");
    }
  }

  /** An absolute http(s) URL without credentials, query or fragment. */
  private static boolean isValidBaseUrl(String baseUrl) {
    if (baseUrl == null) {
      return false;
    }
    URI uri;
    try {
      uri = URI.create(baseUrl);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        && uri.getHost() != null
        && uri.getUserInfo() == null
        && uri.getQuery() == null
        && uri.getFragment() == null;
  }

  public static RagConfig disabled() {
    return new RagConfig(
        false,
        EmbeddingProvider.OLLAMA,
        "http://localhost:11434",
        "embeddinggemma",
        null,
        200000,
        20,
        400000,
        1200,
        6000,
        4000,
        5,
        0.2);
  }
}
