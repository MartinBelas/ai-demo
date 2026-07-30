package ai.demo.console;

import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.service.ChatService;
import java.util.Scanner;

/**
 * Console-based chat interface for interacting with the AI. Handles user input and displays AI
 * responses.
 */
public class ConsoleChat {

  private static final String EXIT_COMMAND = "exit";
  private static final String QUIT_COMMAND = "quit";
  private static final String SHORT_QUIT_COMMAND = "q";

  private final ChatService chatService;
  private final AppConfig config;

  /**
   * Creates a new ConsoleChat.
   *
   * @param chatService the chat service to use for AI interactions
   * @param config the application configuration
   */
  public ConsoleChat(ChatService chatService, AppConfig config) {
    this.chatService = chatService;
    this.config = config;
  }

  /** Starts the console chat interface. Runs until the user enters an exit command. */
  public void start() {

    printHeader();

    final Conversation conversation = new Conversation();

    try (Scanner scanner = new Scanner(System.in)) {

      while (true) {

        final String question = readQuestion(scanner);

        if (shouldExit(question)) {
          break;
        }

        ask(question, conversation);
      }
    }

    printGoodbye();
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

    ChatResponse response = chatService.ask(conversation);

    conversation.add(ChatMessage.assistant(response.answer()));

    printResponse(response);
  }

  private boolean shouldExit(String question) {
    final String normalized = question.trim().toLowerCase();
    return normalized.equals(EXIT_COMMAND)
        || normalized.equals(QUIT_COMMAND)
        || normalized.equals(SHORT_QUIT_COMMAND);
  }

  private void printResponse(ChatResponse response) {

    System.out.println();
    System.out.println("AI  > " + response.answer());
    System.out.println();
    System.out.println("Time: " + response.durationMs() + " ms");
    System.out.println();
  }

  private void printGoodbye() {
    System.out.println("Bye!");
  }
}
