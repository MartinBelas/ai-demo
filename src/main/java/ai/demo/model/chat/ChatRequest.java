package ai.demo.model.chat;

public record ChatRequest(String question) {
    public ChatRequest {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be null or empty");
        }
    }
}
