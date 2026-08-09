package ai.demo.console.command;

import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.ChatMessage;

public class HistoryCommand implements ConsoleCommand {

  @Override
  public String name() {
    return "/history";
  }

  @Override
  public String description() {
    return "Show conversation history";
  }

  @Override
  public CommandResult execute(ConsoleContext context) {

    if (context.conversation().isEmpty()) {
      return CommandResult.success("Conversation is empty.");
    }

    String message =
        context.conversation().messages().stream()
            .map(this::formatMessage)
            .reduce((first, second) -> first + "\n" + second)
            .orElse("");

    return CommandResult.success(message);
  }

  private String formatMessage(ChatMessage message) {
    String role =
        switch (message.role()) {
          case USER -> "You";
          case ASSISTANT -> "AI";
          case SYSTEM -> "System";
        };

    return role + ": " + message.content();
  }
}
