package ai.demo.model.chat;

public record ChatResponse(
        String answer,
        String model,
        long durationMs
) {
    public ChatResponse {
        if (answer == null) {
            throw new IllegalArgumentException("Answer cannot be null");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }
}