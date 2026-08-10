package ai.demo.console.command;

import ai.demo.console.ConsoleContext;

public class ExitCommand implements ConsoleCommand {

  @Override
  public String name() {
    return "/exit";
  }

  @Override
  public String description() {
    return "Exit the console";
  }

  @Override
  public CommandResult execute(ConsoleContext context, String[] args) {
    return CommandResult.exit();
  }
}
