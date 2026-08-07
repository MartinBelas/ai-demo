package ai.demo.console;

import ai.demo.config.AppConfig;
import ai.demo.console.command.CommandResult;
import ai.demo.console.command.CommandStatus;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.service.ChatService;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based chat interface for interacting with the AI. Handles user input and displays AI
 * responses.
 */
public class ConsoleChat {

  private static final String EXIT_COMMAND = "exit";
  private static final String QUIT_COMMAND = "quit";
  private static final String SHORT_QUIT_COMMAND = "q";

  private static final Logger log = LoggerFactory.getLogger(ConsoleChat.class);

  private final ChatService chatService;
  private final AppConfig config;
  private final ConsoleCommandDispatcher commandDispatcher;

  /**
   * Creates a new ConsoleChat.
   *
   * @param chatService the chat service to use for AI interactions
   * @param config the application configuration
   */
  public ConsoleChat(
      ChatService chatService, AppConfig config, ConsoleCommandDispatcher commandDispatcher) {
    this.chatService = chatService;
    this.config = config;
    this.commandDispatcher = commandDispatcher;
  }

  /** Starts the console chat interface. Runs until the user enters an exit command. */
  public void start() {

    printHeader();

    ConsoleContext context = new ConsoleContext(new Conversation());

    try (Scanner scanner = new Scanner(System.in)) {

      boolean exit = false;
      while (!exit) {

        String input = readQuestion(scanner);

        if (input.startsWith("/")) {

          CommandResult result = commandDispatcher.dispatch(input, context);

          if (result.message() != null) {
            System.out.println(result.message());
          }

          if (result.status() == CommandStatus.EXIT) {
            exit = true;
          }
        } else {
          ask(input, context.conversation());
        }
      }
    }
  }

  private void printHeader() {

    System.out.println("==================================");
    System.out.println(" AI Demo");
    System.out.println(" Model: " + config.model());
    System.out.println(
        " Type '"
            + EXIT_COMMAND
            + "', '"
            + QUIT_COMMAND
            + "', or '"
            + SHORT_QUIT_COMMAND
            + "' to quit.");
    System.out.println("==================================");
    System.out.println();
  }

  private String readQuestion(Scanner scanner) {
    System.out.print("You > ");
    return scanner.nextLine();
  }

  private void ask(final String question, final Conversation conversation) {

    conversation.add(ChatMessage.user(question));

    StringBuilder answer = new StringBuilder();
    long start = System.currentTimeMillis();

    try {

      System.out.print("AI: ");

      chatService.askStreaming(
          conversation,
          chunk -> {
            System.out.print(chunk.content());
            answer.append(chunk.content());
          });

      System.out.println();

      long duration = System.currentTimeMillis() - start;

      String finalAnswer = answer.toString();
      conversation.add(ChatMessage.assistant(finalAnswer));

      printSummary(finalAnswer, duration);

    } catch (LlmException e) {

      log.error("LLM request failed", e);

      System.out.println();
      System.out.println("Unable to communicate with the AI model: " + e.getMessage());
    }
  }

  private void printSummary(String answer, long durationMs) {
    System.out.println();
    System.out.println("==================================");
    System.out.println(" AI Response Summary");
    System.out.println("==================================");
    System.out.println(" Answer length: " + answer.length() + " characters");
    System.out.println(" Duration:      " + durationMs + " ms");
    System.out.println("==================================");
    System.out.println();
  }
}
