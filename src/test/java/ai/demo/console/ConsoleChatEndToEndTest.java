package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.agent.AgentLlmGateway;
import ai.demo.agent.ToolCallingAgent;
import ai.demo.agent.tool.CalculatorTool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.console.command.CommandRegistry;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.FileConversationRepository;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleChatEndToEndTest {

  @TempDir Path tempDir;

  @Test
  void shouldRunQuestionThroughAgentAndPersistConversation() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    Path conversationFile = tempDir.resolve("conversation.json");
    var repository = new FileConversationRepository(conversationFile, objectMapper);
    var agent =
        new ToolCallingAgent(
            new DirectReplyGateway(),
            new ToolDescriptionFormatter(),
            List.of(new CalculatorTool()),
            objectMapper);
    var service = new ChatService(agent);
    var dispatcher = new ConsoleCommandDispatcher(new CommandRegistry().commands());
    var config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.4, 300, "Be helpful."),
            new OllamaConfig("fake-model", "http://localhost:11434", 4096, 1.18),
            null,
            conversationFile);
    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    try (var input = new ByteArrayInputStream("Hello\n/exit\n".getBytes(StandardCharsets.UTF_8));
        var printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setIn(input);
      System.setOut(printStream);
      new ConsoleChat(service, config, dispatcher, repository).start();
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }

    Conversation persisted = repository.load();
    assertEquals(2, persisted.messages().size());
    assertEquals("Hello", persisted.messages().getFirst().content());
    assertEquals("End-to-end answer", persisted.messages().getLast().content());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("AI: End-to-end answer"));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Total tokens:      10"));
  }

  private static final class DirectReplyGateway implements AgentLlmGateway {
    @Override
    public LlmResponse request(Conversation conversation, Map<String, String> variables) {
      throw new UnsupportedOperationException("The agent uses streaming");
    }

    @Override
    public StreamingResult stream(
        Conversation conversation, Map<String, String> variables, Consumer<ChatChunk> consumer) {
      consumer.accept(
          new ChatChunk(
              "{\"type\":\"model_reply\",\"content\":\"End-to-end answer\"}",
              ChatChunkType.CONTENT,
              true));
      return new StreamingResult("fake-model", new TokenUsage(6, 4));
    }
  }
}
