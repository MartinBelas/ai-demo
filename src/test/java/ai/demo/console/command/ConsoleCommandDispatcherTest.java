package ai.demo.console.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.Conversation;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsoleCommandDispatcherTest {

  @Test
  void shouldExecuteMatchingCommand() {

    ConsoleCommand mockCommand = mock(ConsoleCommand.class);
    ConsoleContext context = new ConsoleContext(new Conversation());

    when(mockCommand.execute(context)).thenReturn(CommandResult.success("OK"));

    ConsoleCommandDispatcher dispatcher =
        new ConsoleCommandDispatcher(Map.of("/test", mockCommand));

    CommandResult result = dispatcher.dispatch("/test", context);

    assertNotNull(result);
    assertEquals(CommandStatus.SUCCESS, result.status());
    assertEquals("OK", result.message());
    verify(mockCommand).execute(context);
  }

  @Test
  void shouldReturnFailureForUnknownCommand() {

    ConsoleCommandDispatcher dispatcher = new ConsoleCommandDispatcher(Map.of());

    ConsoleContext context = new ConsoleContext(new Conversation());

    CommandResult result = dispatcher.dispatch("/unknown", context);

    assertNotNull(result);
    assertEquals(CommandStatus.FAILURE, result.status());
    assertEquals("Unknown command: /unknown", result.message());
  }

  @Test
  void shouldRegisterDefaultCommands() {

    CommandRegistry registry = new CommandRegistry();

    assertEquals(4, registry.commands().size());
    assertEquals(HelpCommand.class, registry.commands().get("/help").getClass());
    assertEquals(HistoryCommand.class, registry.commands().get("/history").getClass());
    assertEquals(NewCommand.class, registry.commands().get("/new").getClass());
    assertEquals(ExitCommand.class, registry.commands().get("/exit").getClass());
  }
}
