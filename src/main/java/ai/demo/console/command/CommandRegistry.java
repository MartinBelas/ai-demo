package ai.demo.console.command;

import ai.demo.client.LlmProviderSelector;
import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {

  private final LlmProviderSelector providerSelector;

  public CommandRegistry() {
    this(null);
  }

  public CommandRegistry(LlmProviderSelector providerSelector) {
    this.providerSelector = providerSelector;
  }

  public Map<String, ConsoleCommand> commands() {
    Map<String, ConsoleCommand> commands = new HashMap<>();
    commands.put("/help", new HelpCommand(providerSelector));
    commands.put("/new", new NewCommand());
    commands.put("/history", new HistoryCommand());
    commands.put("/thinking", new ThinkingCommand());
    commands.put("/exit", new ExitCommand());
    if (providerSelector != null) commands.put("/llm", new ProviderCommand(providerSelector));
    return Map.copyOf(commands);
  }
}
