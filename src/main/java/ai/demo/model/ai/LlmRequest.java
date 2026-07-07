package ai.demo.model.ai;

public record LlmRequest(
        String prompt
) {
    public LlmRequest {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
    }
}
