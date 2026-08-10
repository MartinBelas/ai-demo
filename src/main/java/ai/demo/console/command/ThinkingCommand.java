package ai.demo.console.command;

import ai.demo.console.ConsoleContext;

public class ThinkingCommand implements ConsoleCommand {

  @Override
  public String name() {
    return "/thinking";
  }

  @Override
  public String description() {
    return "Controls reasoning output: ON, OFF, MINIMAL, STATUS";
  }

  @Override
  public CommandResult execute(ConsoleContext context, String[] args) {
    if (args.length == 0) {
      return CommandResult.failure("Usage: /thinking ON|OFF|MINIMAL|STATUS");
    }

    String mode = args[0].trim().toUpperCase();

    ThinkingMode next;

    try {
      next = ThinkingMode.valueOf(mode);
    } catch (IllegalArgumentException e) {
      return CommandResult.failure("Usage: /thinking ON|OFF|MINIMAL|STATUS");
    }

    if (next == ThinkingMode.STATUS) {
      return CommandResult.success("Thinking mode is: " + context.thinkingMode());
    }

    context.setThinkingMode(next);
    return CommandResult.success("Thinking mode set to: " + next);
  }
}
