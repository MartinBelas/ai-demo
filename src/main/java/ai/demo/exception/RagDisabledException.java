package ai.demo.exception;

public final class RagDisabledException extends RagException {
  public RagDisabledException() {
    super("RAG is disabled");
  }
}
