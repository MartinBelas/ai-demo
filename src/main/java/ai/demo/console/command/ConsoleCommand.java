package ai.demo.console.command;

import ai.demo.console.ConsoleContext;

public interface ConsoleCommand {

  String name();

  String description();

  CommandResult execute(ConsoleContext context);
}
