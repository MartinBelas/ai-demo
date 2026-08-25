package ai.demo.config;

/** HTTP server configuration. */
public record ServerConfig(int port) {

  public ServerConfig {
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("server port must be between 1 and 65535");
    }
  }
}
