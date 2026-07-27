package ai.demo.model.chat;

import java.util.ArrayList;
import java.util.List;

public class Conversation {

    private final List<ChatMessage> messages = new ArrayList<>();

    public void add(ChatMessage message) {
        messages.add(message);
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int size() {
        return messages.size();
    }
}
