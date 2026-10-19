package ai.demo.client.agentlab;

import java.util.Optional;

/** Supplies the {@code Authorization} header value for an agent service call. */
@FunctionalInterface
public interface AgentLabAuthorization {

  /** No credentials, for a local agent service. */
  AgentLabAuthorization NONE = Optional::empty;

  /**
   * @return the header value, e.g. {@code Bearer <token>}, or empty to send no header
   * @throws ai.demo.exception.AgentServiceException when credentials cannot be obtained
   */
  Optional<String> header();
}
