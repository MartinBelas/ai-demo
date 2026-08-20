package ai.demo.console;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.ToolCallEvent;
import ai.demo.agent.ToolResultEvent;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.console.command.CommandResult;
import ai.demo.console.command.CommandStatus;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.console.command.ThinkingMode;
import ai.demo.exception.LlmException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.ConversationRepository;
import ai.demo.service.ChatService;
import java.util.Locale;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based chat interface for interacting with the AI. Handles user input and displays AI
 * responses.
 */
public class ConsoleChat {

  private static final String ANSI_GRAY = "\u001B[90m";
  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_ITALIC = "\u001B[3m";
  private static final String SECTION_SEPARATOR = "==================================";
  private static final int MINIMAL_THINKING_LIMIT = 200;
  private static final int ON_THINKING_FLUSH_LIMIT = 120;

  private static final Logger log = LoggerFactory.getLogger(ConsoleChat.class);

  private final ChatService chatService;
  private final AppConfig config;
  private final ConsoleCommandDispatcher commandDispatcher;
  private final ConversationRepository conversationRepository;

  /**
   * Creates a new ConsoleChat.
   *
   * @param chatService the chat service to use for AI interactions
   * @param config the application configuration
   * @param commandDispatcher the command dispatcher for handling console commands
   * @param conversationRepository the repository for managing conversation data
   */
  public ConsoleChat(
      ChatService chatService,
      AppConfig config,
      ConsoleCommandDispatcher commandDispatcher,
      ConversationRepository conversationRepository) {

    this.chatService = chatService;
    this.config = config;
    this.commandDispatcher = commandDispatcher;
    this.conversationRepository = conversationRepository;
  }

  /** Starts the console chat interface. Runs until the user enters an exit command. */
  public void start() {

    printHeader();

    ConsoleContext context = new ConsoleContext(loadConversation());

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

  private void ask(String question, ConsoleContext context) {

    Conversation conversation = context.conversation();
    ThinkingOutput thinkingOutput = new ThinkingOutput();

    conversation.add(ChatMessage.user(question));

    try {

      ChatResponse response =
          chatService.ask(conversation, event -> printAgentEvent(event, context, thinkingOutput));

      flushThinking(context, thinkingOutput);

      conversation.add(ChatMessage.assistant(response.answer()));

      conversationRepository.save(conversation);

      printSummary(response);

    } catch (LlmException e) {

      log.error("LLM request failed", e);

      System.out.println();
      System.out.println("Unable to communicate with the AI model: " + e.getMessage());
      System.out.println();
    }
  }

  private void printAgentEvent(
      AgentEvent event, ConsoleContext context, ThinkingOutput thinkingOutput) {

    if (event instanceof ThinkingEvent(String content1)) {

      printThinking(content1, context, thinkingOutput);

    } else if (event instanceof ToolCallEvent(String toolName, String input)) {

      flushThinking(context, thinkingOutput);

      System.out.println();
      System.out.println();
      System.out.println("Using tool: " + toolName);

      System.out.println("Input: " + input);

    } else if (event instanceof ToolResultEvent toolResult) {

      System.out.println("Tool result: " + toolResult.content());

      System.out.println();

    } else if (event instanceof ContentEvent(String content1)) {

      flushThinking(context, thinkingOutput);

      System.out.println();
      System.out.println("AI: " + content1);
    }
  }

  private void printThinking(
      String content, ConsoleContext context, ThinkingOutput thinkingOutput) {

    ThinkingMode mode = context.thinkingMode();

    switch (mode) {
      case OFF, STATUS -> {
        // Thinking output is intentionally suppressed.
      }
      case MINIMAL -> {
        if (thinkingOutput.printed) {
          return;
        }

        appendWithSeparator(thinkingOutput.buffer, content);

        if (thinkingOutput.buffer.length() >= MINIMAL_THINKING_LIMIT) {
          printStyledThinking(thinkingOutput.buffer.substring(0, MINIMAL_THINKING_LIMIT) + "...");
          thinkingOutput.buffer.setLength(0);
          thinkingOutput.printed = true;
        }
      }
      case ON -> {
        appendWithSeparator(thinkingOutput.buffer, content);

        if (thinkingOutput.buffer.length() >= ON_THINKING_FLUSH_LIMIT || content.endsWith(".")) {
          printStyledThinking(thinkingOutput.buffer.toString());
          thinkingOutput.buffer.setLength(0);
        }
      }
    }
  }

  private void flushThinking(ConsoleContext context, ThinkingOutput thinkingOutput) {
    if (thinkingOutput.buffer.isEmpty()) {
      return;
    }

    ThinkingMode mode = context.thinkingMode();

    if ((mode == ThinkingMode.MINIMAL && !thinkingOutput.printed) || mode == ThinkingMode.ON) {
      printStyledThinking(thinkingOutput.buffer.toString());
    }

    thinkingOutput.buffer.setLength(0);
    thinkingOutput.printed = true;
  }

  private void appendWithSeparator(StringBuilder buffer, String content) {
    if (!buffer.isEmpty()) {
      buffer.append(' ');
    }
    buffer.append(content);
  }

  private void printStyledThinking(String content) {
    System.out.println(ANSI_GRAY + ANSI_ITALIC + content + ANSI_RESET);
  }

  private Conversation loadConversation() {
    return conversationRepository.load();
  }

  private void printHeader() {

    System.out.println(SECTION_SEPARATOR);

    System.out.println(" AI Demo");

    System.out.println(" Model: " + config.model());

    System.out.println(SECTION_SEPARATOR);

    System.out.println();
  }

  private String readQuestion(Scanner scanner) {

    System.out.print("You > ");

    return scanner.nextLine();
  }

  private void printSummary(ChatResponse response) {

    TokenUsage tokenUsage = response.tokenUsage();

    System.out.println();

    System.out.println(SECTION_SEPARATOR);

    System.out.println(" AI Response Summary");

    System.out.println(SECTION_SEPARATOR);

    System.out.println(" Model:             " + response.model());
    System.out.println(" Prompt tokens:     " + tokenUsage.promptTokens());
    System.out.println(" Completion tokens: " + tokenUsage.completionTokens());
    System.out.println(" Total tokens:      " + tokenUsage.totalTokens());
    System.out.println(" Duration:          " + formatDuration(response.durationMs()));
    System.out.println(" Response:          " + response.answer());

    System.out.println(SECTION_SEPARATOR);

    System.out.println();
  }

  private String formatDuration(long durationMs) {

    long minutes = durationMs / 60_000;

    long seconds = (durationMs % 60_000) / 1_000;

    long millis = durationMs % 1_000;

    return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
  }

  private static final class ThinkingOutput {
    private final StringBuilder buffer = new StringBuilder();
    private boolean printed;
  }
}
