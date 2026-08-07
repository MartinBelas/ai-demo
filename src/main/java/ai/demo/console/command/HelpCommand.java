package ai.demo.console.command;

import ai.demo.console.ConsoleContext;

public class HelpCommand implements ConsoleCommand {

  @Override
  public String name() {
    return "";
  }

  @Override
  public String description() {
    return "";
  }

  @Override
  public CommandResult execute(ConsoleContext context) {
    return CommandResult.success(
"""
Available commands:

/help
/new
/exit
""");
  }
}
