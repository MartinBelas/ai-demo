package ai.demo.console.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class NewCommandTest {

  @Test
  void shouldStartNewConversation() {

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    ConsoleContext context = new ConsoleContext(conversation);

    NewCommand command = new NewCommand();

    CommandResult result = command.execute(context, null);

    assertTrue(context.conversation().isEmpty());
    assertEquals("New conversation started.", result.message());
    assertNotSame(conversation, context.conversation());
  }
}
