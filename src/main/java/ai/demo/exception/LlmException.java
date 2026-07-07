package ai.demo.exception;

/**
 * Exception thrown when LLM (Large Language Model) operations fail.
 * Wraps underlying exceptions from HTTP communication, JSON parsing, or LLM provider errors.
 */
public class LlmException extends RuntimeException {

    /**
     * Creates a new LlmException.
     *
     * @param message the error message
     * @param cause the underlying cause of the exception
     */
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}