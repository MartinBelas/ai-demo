package ai.demo.model.chat;

public record ChatResponse(String answer, String model, long durationMs) {
  public ChatResponse {

    if (answer == null || answer.isBlank()) {
      throw new IllegalArgumentException("answer must not be blank");
    }

    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be blank");
    }

    if (durationMs < 0) {
      throw new IllegalArgumentException("durationMs must not be negative");
    }
  }
}
