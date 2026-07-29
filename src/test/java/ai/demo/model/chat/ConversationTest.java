package ai.demo.model.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationTest {

    @Test
    void shouldCreateEmptyConversation() {

        var conversation = new Conversation();

        assertEquals(0, conversation.messages().size());
    }

    @Test
    void shouldAddMessages() {

        var conversation = new Conversation();

        conversation.add(ChatMessage.user("Hello"));
        conversation.add(ChatMessage.assistant("Hi"));

        assertEquals(2, conversation.messages().size());
    }

    @Test
    void shouldPreserveMessageOrder() {

        var conversation = new Conversation();

        conversation.add(ChatMessage.user("Hello"));
        conversation.add(ChatMessage.assistant("Hi"));

        assertEquals(
                Role.USER,
                conversation.messages().getFirst().role());

        assertEquals(
                Role.ASSISTANT,
                conversation.messages().getLast().role());
    }
}