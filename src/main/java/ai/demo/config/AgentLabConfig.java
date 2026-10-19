package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.net.URI;
import java.time.Duration;

/**
 * Connection to the optional ai-agent-lab service: a separate Python agent with tools such as web
 * search.
 *
 * <p>Agent mode is independent of the selected chat {@link LlmProvider}; the agent service chooses
 * its own model. The base URL is required and validated only when agent mode is enabled. On Cloud
 * Run the agent service is private, so calls use {@link AgentLabAuthentication#GOOGLE_ID_TOKEN}.
 */
public record AgentLabConfig(
    boolean enabled, String baseUrl, Duration timeout, AgentLabAuthentication authentication) {

  private static final Duration MIN_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration MAX_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(70);

  public AgentLabConfig(boolean enabled, String baseUrl, Duration timeout) {
    this(enabled, baseUrl, timeout, AgentLabAuthentication.NONE);
  }

  public AgentLabConfig {
    if (authentication == null) {
      throw new ConfigurationException("Agent lab authentication must be set");
    }
    if (timeout == null
        || timeout.compareTo(MIN_TIMEOUT) < 0
        || timeout.compareTo(MAX_TIMEOUT) > 0) {
      throw new ConfigurationException("Agent lab timeout must be between 1 second and 5 minutes");
    }
    if (enabled) {
      baseUrl = validatedBaseUrl(baseUrl);
    }
  }

  public static AgentLabConfig disabled() {
    return new AgentLabConfig(false, null, DEFAULT_TIMEOUT);
  }

  private static String validatedBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new ConfigurationException("Agent lab base URL is required when agent mode is enabled");
    }
    String normalized = baseUrl.strip();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    try {
      URI uri = URI.create(normalized);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException("Unsupported agent lab base URL");
      }
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException("Invalid agent lab base URL", e);
    }
    return normalized;
  }
}
