package ai.demo.console.command;

public record CommandResult(CommandStatus status, String message) {

  public static CommandResult success(String message) {
    return new CommandResult(CommandStatus.SUCCESS, message);
  }

  public static CommandResult failure(String message) {
    return new CommandResult(CommandStatus.FAILURE, message);
  }

  public static CommandResult exit() {
    return new CommandResult(CommandStatus.EXIT, "Exiting...");
  }
}
