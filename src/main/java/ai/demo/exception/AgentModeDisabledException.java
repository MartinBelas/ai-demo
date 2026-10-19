package ai.demo.exception;

/** Thrown when a request asks for agent mode but the deployment does not enable it. */
public final class AgentModeDisabledException extends RuntimeException {

  public AgentModeDisabledException() {
    super("Agent mode is disabled");
  }
}
