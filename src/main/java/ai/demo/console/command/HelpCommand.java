package ai.demo.console.command;

import ai.demo.client.LlmProviderSelector;
import ai.demo.console.ConsoleContext;

public class HelpCommand implements ConsoleCommand {

  private final LlmProviderSelector providerSelector;

  public HelpCommand() {
    this(null);
  }

  public HelpCommand(LlmProviderSelector providerSelector) {
    this.providerSelector = providerSelector;
  }

  @Override
  public String name() {
    return "/help";
  }

  @Override
  public String description() {
    return "Shows all available commands";
  }

  @Override
  public CommandResult execute(ConsoleContext context, String[] args) {

    StringBuilder sb = new StringBuilder();
    sb.append("Available commands:\n\n");

    new CommandRegistry(providerSelector)
        .commands()
        .forEach(
            (name, cmd) -> sb.append(name).append("  -  ").append(cmd.description()).append("\n"));

    return CommandResult.success(sb.toString());
  }
}
