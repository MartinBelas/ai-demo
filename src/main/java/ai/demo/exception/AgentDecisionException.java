package ai.demo.exception;

public class AgentDecisionException extends RuntimeException {

  public AgentDecisionException(String message) {
    super(message);
  }

  public AgentDecisionException(String message, Throwable cause) {
    super(message, cause);
  }
}
