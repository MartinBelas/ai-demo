package ai.demo.model.ai;

public record LlmResponse(
        String text,
        String model
) {
    public LlmResponse {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
    }
}