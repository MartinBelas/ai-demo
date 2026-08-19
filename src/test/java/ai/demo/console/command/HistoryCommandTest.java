package ai.demo.console.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import org.junit.jupiter.api.Test;

class HistoryCommandTest {

  @Test
  void shouldReturnConversationHistory() {

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hello", null));
    conversation.add(new ChatMessage(Role.ASSISTANT, "Hi!", null));

    ConsoleContext context = new ConsoleContext(conversation);

    HistoryCommand command = new HistoryCommand();

    CommandResult result = command.execute(context, null);

    assertEquals(CommandStatus.SUCCESS, result.status());
    assertEquals(
        """
                You: Hello
                AI: Hi!
                """
            .trim(),
        result.message());
  }

  @Test
  void shouldReturnMessageWhenConversationIsEmpty() {

    ConsoleContext context = new ConsoleContext(new Conversation());

    HistoryCommand command = new HistoryCommand();

    CommandResult result = command.execute(context, null);

    assertEquals(CommandStatus.SUCCESS, result.status());
    assertEquals("Conversation is empty.", result.message());
  }
}
