package ai.demo.console.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import ai.demo.console.ConsoleContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsoleCommandDispatcherTest {

  @Test
  void shouldExecuteMatchingCommand() {
    // Arrange
    ConsoleCommand mockCommand = mock(ConsoleCommand.class);
    ConsoleContext context = new ConsoleContext(null);

    when(mockCommand.execute(context)).thenReturn(CommandResult.success("OK"));

    ConsoleCommandDispatcher dispatcher =
        new ConsoleCommandDispatcher(Map.of("/test", mockCommand));

    // Act
    CommandResult result = dispatcher.dispatch("/test", context);

    // Assert
    assertNotNull(result);
    assertEquals(CommandStatus.SUCCESS, result.status());
    assertEquals("OK", result.message());
    verify(mockCommand).execute(context);
  }

  @Test
  void shouldIgnoreUnknownCommand() {
    // Arrange
    ConsoleCommandDispatcher dispatcher = new ConsoleCommandDispatcher(Map.of()); // empty registry

    ConsoleContext context = new ConsoleContext(null);

    // Act
    CommandResult result = dispatcher.dispatch("/unknown", context);

    // Assert
    assertNotNull(result);
    assertEquals(CommandStatus.FAILURE, result.status());
    // message may vary depending on your implementation
  }
}
