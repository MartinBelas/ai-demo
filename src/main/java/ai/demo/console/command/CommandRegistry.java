package ai.demo.console.command;

import java.util.Map;

public class CommandRegistry {

  public Map<String, ConsoleCommand> commands() {
    return Map.of(
        "/help", new HelpCommand(),
        "/new", new NewCommand(),
        "/history", new HistoryCommand(),
        "/exit", new ExitCommand());
  }
}
