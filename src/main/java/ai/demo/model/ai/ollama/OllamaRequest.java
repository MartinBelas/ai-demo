package ai.demo.model.ai.ollama;

import ai.demo.model.ai.LlmRequest;

public record OllamaRequest(
        String model,
        String prompt,
        boolean stream,
        OllamaOptions options
) {
    public OllamaRequest {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("prompt cannot be null or empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
    }

    public static OllamaRequest from(
            LlmRequest request,
            String model,
            OllamaOptions options) {

        return new OllamaRequest(
                model,
                request.prompt(),
                false,
                options);
    }
}