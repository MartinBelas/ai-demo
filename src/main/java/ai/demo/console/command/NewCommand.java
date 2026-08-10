package ai.demo.console.command;

import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.Conversation;

public class NewCommand implements ConsoleCommand {

  @Override
  public String name() {
    return "/new";
  }

  @Override
  public String description() {
    return "Start a new conversation";
  }

  @Override
  public CommandResult execute(ConsoleContext context, String[] args) {
    context.setConversation(new Conversation());
    return CommandResult.success("New conversation started.");
  }
}
