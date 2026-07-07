package ai.demo.model.ai.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaResponse(
        String response,
        String model,
        boolean done
) {
    public OllamaResponse {
        if (response == null) {
            throw new IllegalArgumentException("response cannot be null");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
    }
}