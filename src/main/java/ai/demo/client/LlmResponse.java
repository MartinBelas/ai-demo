package ai.demo.client;

public record LlmResponse(
        String text,
        String model
) {

    public LlmResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }
}