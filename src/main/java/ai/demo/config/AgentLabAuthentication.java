package ai.demo.config;

import java.util.Locale;

/** How calls to the agent service are authenticated. */
public enum AgentLabAuthentication {
  /** No credentials: for a local agent service only. */
  NONE,
  /**
   * A Google-signed identity token of the runtime service account, fetched from the metadata
   * server: the agent service runs as a private Cloud Run service that only the AI Demo service
   * account may invoke.
   */
  GOOGLE_ID_TOKEN;

  public static AgentLabAuthentication from(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Agent lab authentication must be set");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported agent lab authentication: " + value, e);
    }
  }
}
