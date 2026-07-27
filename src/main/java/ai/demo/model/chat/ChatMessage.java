package ai.demo.model.chat;

public record ChatMessage(
        Role role,
        String content) {

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(Role.SYSTEM, text);
    }
}