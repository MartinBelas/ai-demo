package ai.demo.console.command;

import ai.demo.console.ConsoleContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class ConsoleCommandDispatcher {

  private final Map<String, ConsoleCommand> commands;

  public ConsoleCommandDispatcher(Map<String, ConsoleCommand> commands) {
    this.commands = commands;
  }

  public CommandResult dispatch(String input, ConsoleContext context) {

    String[] parts = input.trim().split("\\s+");

    String commandName = parts[0];

    String[] args = new String[0];
    if (parts.length > 1) {
      args = Arrays.copyOfRange(parts, 1, parts.length);
    }

    ConsoleCommand command = commands.get(commandName);

    if (command == null) {
      return CommandResult.failure("Unknown command: " + commandName);
    }

    return command.execute(context, args);
  }

  public Collection<ConsoleCommand> commands() {
    return commands.values();
  }
}
