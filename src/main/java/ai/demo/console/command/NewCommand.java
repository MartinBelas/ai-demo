package ai.demo.console.command;

import ai.demo.console.ConsoleContext;

public class NewCommand implements ConsoleCommand {

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
    return CommandResult.success("Started a new conversation.");
  }
}
