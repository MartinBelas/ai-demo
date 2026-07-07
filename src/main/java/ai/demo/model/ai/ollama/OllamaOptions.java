package ai.demo.model.ai.ollama;

public record OllamaOptions(
        int numPredict,
        int numCtx,
        double temperature
) {
    public OllamaOptions {
        if (numPredict < 1) {
            throw new IllegalArgumentException("numPredict must be positive");
        }
        if (numCtx < 1) {
            throw new IllegalArgumentException("numCtx must be positive");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
    }
}