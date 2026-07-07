package ai.demo.console;

import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatRequest;
import ai.demo.model.chat.ChatResponse;
import ai.demo.service.ChatService;

import java.util.Scanner;

/**
 * Console-based chat interface for interacting with the AI.
 * Handles user input and displays AI responses.
 */
public class ConsoleChat implements AutoCloseable {

    private static final String EXIT_COMMAND = "exit";
    private static final String QUIT_COMMAND = "quit";
    private static final String SHORT_QUIT_COMMAND = "q";

    private final Scanner scanner = new Scanner(System.in);
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

    @Override
    public void close() {
        scanner.close();
    }

    /**
     * Starts the console chat interface.
     * Runs until the user enters an exit command.
     */
    public void start() {

        printHeader();

        while (true) {

            final String question = readQuestion();

            if (shouldExit(question)) {
                break;
            }

            ask(question);
        }

        printGoodbye();
    }

    private void printHeader() {

        System.out.println("==================================");
        System.out.println(" AI Demo");
        System.out.println(" Model: " + config.model());
        System.out.println(" Type '" + EXIT_COMMAND + "', '" + QUIT_COMMAND + "', or '" + SHORT_QUIT_COMMAND + "' to quit.");
        System.out.println("==================================");
        System.out.println();
    }

    private String readQuestion() {
        while (true) {
            System.out.print("You > ");
            if (!scanner.hasNextLine()) {
                System.out.println("\nInput closed. Exiting.");
                break;
            }
            final String input = scanner.nextLine();
            if (input != null && !input.trim().isEmpty()) {
                return input;
            }
            System.out.println("Please enter a non-empty question.");
        }
        return EXIT_COMMAND;
    }

    private void ask(final String question) {

        final ChatResponse response =
                chatService.ask(new ChatRequest(question));

        printResponse(response);
    }

    private boolean shouldExit(String question) {
        final String normalized = question.trim().toLowerCase();
        return normalized.equals(EXIT_COMMAND) || normalized.equals(QUIT_COMMAND) || normalized.equals(SHORT_QUIT_COMMAND);
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
