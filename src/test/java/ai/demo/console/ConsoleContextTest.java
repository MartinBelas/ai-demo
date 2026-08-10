package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertSame;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class ConsoleContextTest {

  @Test
  void shouldReplaceConversation() {

    Conversation original = new Conversation();
    original.add(ChatMessage.user("Hello"));

    ConsoleContext context = new ConsoleContext(original);

    Conversation replacement = new Conversation();
    replacement.add(ChatMessage.user("New conversation"));

    context.setConversation(replacement);

    assertSame(replacement, context.conversation());
  }
}
