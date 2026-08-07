package ai.demo.console.command;

import ai.demo.console.ConsoleContext;
import java.util.Collection;
import java.util.Map;

public class ConsoleCommandDispatcher {

  private final Map<String, ConsoleCommand> commands;

  public ConsoleCommandDispatcher(Map<String, ConsoleCommand> commands) {
    this.commands = commands;
  }

  public CommandResult dispatch(String input, ConsoleContext context) {

    ConsoleCommand command = commands.get(input);

    if (command == null) {
      return CommandResult.failure("Unknown command: " + input);
    }

    return command.execute(context);
  }

  public Collection<ConsoleCommand> commands() {
    return commands.values();
  }
}
