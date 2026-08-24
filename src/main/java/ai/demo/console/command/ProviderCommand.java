package ai.demo.console.command;

import ai.demo.client.LlmProviderSelector;
import ai.demo.config.LlmProvider;
import ai.demo.console.ConsoleContext;

public final class ProviderCommand implements ConsoleCommand {
  private final LlmProviderSelector selector;

  public ProviderCommand(LlmProviderSelector selector) {
    this.selector = selector;
  }

  @Override
  public String name() {
    return "/llm";
  }

  @Override
  public String description() {
    return "Selects the LLM provider: OLLAMA, OPENAI, GROQ, GEMINI, STATUS";
  }

  @Override
  public CommandResult execute(ConsoleContext context, String[] args) {
    if (args.length != 1) return usage();
    String value = args[0].trim();
    if ("STATUS".equalsIgnoreCase(value)) {
      return CommandResult.success("Active LLM provider: " + selector.activeProvider());
    }
    try {
      LlmProvider provider = LlmProvider.from(value);
      selector.switchTo(provider);
      return CommandResult.success("LLM provider set to: " + provider);
    } catch (RuntimeException e) {
      return CommandResult.failure("Unable to switch LLM provider: " + e.getMessage());
    }
  }

  private CommandResult usage() {
    return CommandResult.failure("Usage: /llm OLLAMA|OPENAI|GROQ|GEMINI|STATUS");
  }
}
