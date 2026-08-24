package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.console.command.CommandResult;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.ConversationRepository;
import ai.demo.service.ChatService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ConsoleChatTest {

  @Test
  void shouldInstantiateWithValidDependencies() {
    var mockService = mock(ChatService.class);
    var mockDispatcher = mock(ConsoleCommandDispatcher.class);
    var mockRepo = mock(ConversationRepository.class);

    AppConfig config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.7, 10, "Be helpful."),
            new OllamaConfig("test-model", "http://localhost:11434", 100, 1.2),
            null,
            Path.of("conv.json"));

    var console = new ConsoleChat(mockService, config, mockDispatcher, mockRepo);

    assertNotNull(console);
  }

  @Test
  void shouldPrintFirstTwoHundredThinkingCharactersForEveryQuestionAndCompleteSummary()
      throws IOException {
    ChatService service = mock(ChatService.class);
    ConsoleCommandDispatcher dispatcher = mock(ConsoleCommandDispatcher.class);
    ConversationRepository repository = mock(ConversationRepository.class);
    AppConfig config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.7, 10, "Be helpful."),
            new OllamaConfig("configured-model", "http://localhost:11434", 100, 1.2),
            null,
            Path.of("conv.json"));
    String thinking = "x".repeat(250);

    when(repository.load()).thenReturn(new Conversation());
    when(dispatcher.dispatch(eq("/exit"), any())).thenReturn(CommandResult.exit());
    when(service.ask(any(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<AgentEvent> consumer = invocation.getArgument(1);
              consumer.accept(new ThinkingEvent(thinking));
              consumer.accept(new ContentEvent("Full answer"));
              return new ChatResponse(
                  "Full answer", "response-model", new TokenUsage(12, 8), 62_345L);
            });

    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    try (ByteArrayInputStream input =
            new ByteArrayInputStream(
                "First question\nSecond question\n/exit\n".getBytes(StandardCharsets.UTF_8));
        PrintStream consoleOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setIn(input);
      System.setOut(consoleOut);

      new ConsoleChat(service, config, dispatcher, repository).start();
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }

    String consoleOutput = output.toString(StandardCharsets.UTF_8);
    String minimalThinking = "x".repeat(200) + "...";

    assertEquals(2, countOccurrences(consoleOutput, minimalThinking));
    assertFalse(consoleOutput.contains("x".repeat(201) + "..."));
    assertTrue(consoleOutput.contains(" Model:             response-model"));
    assertTrue(consoleOutput.contains(" Prompt tokens:     12"));
    assertTrue(consoleOutput.contains(" Completion tokens: 8"));
    assertTrue(consoleOutput.contains(" Total tokens:      20"));
    assertTrue(consoleOutput.contains(" Duration:          01:02.345"));
    assertTrue(consoleOutput.contains(" Response:          Full answer"));
  }

  private int countOccurrences(String text, String value) {
    return (text.length() - text.replace(value, "").length()) / value.length();
  }
}
