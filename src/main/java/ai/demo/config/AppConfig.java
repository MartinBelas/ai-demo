package ai.demo.config;

/**
 * Application configuration record. Contains all configuration parameters for the AI Demo
 * application.
 */
public record AppConfig(
    String baseUrl,
    String model,
    double temperature,
    int numPredict,
    int numCtx,
    double repeatPenalty) {

  public AppConfig {

    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }

    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }

    if (temperature < 0.0 || temperature > 2.0) {
      throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
    }

    if (numPredict < 1) {
      throw new IllegalArgumentException("numPredict must be positive");
    }

    if (numCtx < 1) {
      throw new IllegalArgumentException("numCtx must be positive");
    }

    if (repeatPenalty < 1.0) {
      throw new IllegalArgumentException("repeatPenalty must be at least 1.0");
    }
  }
}
