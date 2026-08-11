package ai.demo.console;

import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.console.command.CommandResult;
import ai.demo.console.command.CommandStatus;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.console.command.ThinkingMode;
import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatChunkType;
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

  private static final String EXIT_COMMAND = "/exit";

  private static final String ANSI_GRAY = "\u001B[90m";
  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_ITALIC = "\u001B[3m";

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
          ask(input, context);
        }
      }
    }
  }

  private void printHeader() {

    System.out.println("==================================");
    System.out.println(" AI Demo");
    System.out.println(" Model: " + config.model());
    System.out.println(" Type '" + EXIT_COMMAND + "' to quit.");
    System.out.println("==================================");
    System.out.println();
  }

  private String readQuestion(Scanner scanner) {
    System.out.print("You > ");
    return scanner.nextLine();
  }

  private void ask(final String question, final ConsoleContext context) {

    Conversation conversation = context.conversation();
    conversation.add(ChatMessage.user(question));

    StringBuilder finalAnswer = new StringBuilder();
    long start = System.currentTimeMillis();

    try {

      final StringBuilder thinkingBuffer = new StringBuilder();
      final int MINIMAL_LIMIT = 200;
      final int ON_FLUSH_LIMIT = 120;

      System.out.println("AI:");

      StreamingResult result =
          chatService.askStreaming(
              conversation,
              chunk -> {
                if (chunk.type() == ChatChunkType.THINKING) {

                  ThinkingMode mode = context.thinkingMode();

                  switch (mode) {
                    case OFF, STATUS -> {
                      return;
                    }

                    case MINIMAL -> {
                      thinkingBuffer.append(chunk.content()).append(" ");

                      if (thinkingBuffer.length() >= MINIMAL_LIMIT) {

                        System.out.println(
                            ANSI_GRAY
                                + ANSI_ITALIC
                                + thinkingBuffer.toString().trim()
                                + "..."
                                + ANSI_RESET);

                        context.setThinkingMode(ThinkingMode.OFF);
                      }

                      return;
                    }

                    case ON -> {
                      thinkingBuffer.append(chunk.content()).append(" ");

                      if (thinkingBuffer.length() >= ON_FLUSH_LIMIT
                          || chunk.content().endsWith(".")) {

                        System.out.println(
                            ANSI_GRAY
                                + ANSI_ITALIC
                                + thinkingBuffer.toString().trim()
                                + ANSI_RESET);

                        thinkingBuffer.setLength(0);
                      }

                      return;
                    }
                  }
                }

                // CONTENT
                System.out.print(chunk.content());
                finalAnswer.append(chunk.content());
              });

      System.out.println();

      long duration = System.currentTimeMillis() - start;

      conversation.add(ChatMessage.assistant(finalAnswer.toString()));

      printSummary(finalAnswer.toString(), duration, result.tokenUsage());

    } catch (LlmException e) {

      log.error("LLM request failed", e);

      System.out.println();
      System.out.println("Unable to communicate with the AI model: " + e.getMessage());
    }
  }

  private void printSummary(String answer, long durationMs, TokenUsage usage) {
    System.out.println();
    System.out.println("==================================");
    System.out.println(" AI Response Summary");
    System.out.println("==================================");
    System.out.println(" Answer length: " + answer.length() + " characters");
    System.out.println(" Duration:      " + durationMs + " ms");
    System.out.println(" Prompt tokens: " + usage.promptTokens());
    System.out.println(" Completion:    " + usage.completionTokens());
    System.out.println(" Total tokens:  " + usage.totalTokens());
    System.out.println();
    System.out.println(" Answer: " + answer.trim());
    System.out.println("==================================");
    System.out.println();
  }
}
