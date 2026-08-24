package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.agent.DefaultAgentLlmGateway;
import ai.demo.agent.ToolCallingAgent;
import ai.demo.agent.tool.CalculatorTool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.client.SwitchableLlmClient;
import ai.demo.client.TokenUsage;
import ai.demo.config.AppConfig;
import ai.demo.config.GenerationConfig;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.config.OpenAiConfig;
import ai.demo.console.command.CommandRegistry;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatChunkType;
import ai.demo.model.prompt.Prompt;
import ai.demo.persistence.FileConversationRepository;
import ai.demo.prompt.PromptComposer;
import ai.demo.prompt.template.SystemPromptProvider;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderSwitchEndToEndTest {

  @TempDir Path tempDir;

  @Test
  void shouldPreserveConversationWhenProviderChanges() throws Exception {
    ScriptedClient ollama = new ScriptedClient("ollama-model", "Ollama answer");
    ScriptedClient openAi = new ScriptedClient("openai-model", "OpenAI answer");
    var switchable =
        new SwitchableLlmClient(
            LlmProvider.OLLAMA,
            Map.of(LlmProvider.OLLAMA, () -> ollama, LlmProvider.OPENAI, () -> openAi));
    SystemPromptProvider promptProvider = mock(SystemPromptProvider.class);
    when(promptProvider.getSystemPrompt(anyMap())).thenReturn("Agent instructions");
    var gateway = new DefaultAgentLlmGateway(switchable, new PromptComposer(promptProvider));
    var agent =
        new ToolCallingAgent(
            gateway,
            new ToolDescriptionFormatter(),
            java.util.List.of(new CalculatorTool()),
            new ObjectMapper());
    Path conversationFile = tempDir.resolve("conversation.json");
    var repository = new FileConversationRepository(conversationFile, new ObjectMapper());
    var config =
        new AppConfig(
            LlmProvider.OLLAMA,
            new GenerationConfig(0.4, 300, "Be helpful."),
            new OllamaConfig("ollama-model", "http://localhost:11434", 4096, 1.18),
            new OpenAiConfig("openai-model", "https://api.openai.com/v1", "OPENAI_API_KEY"),
            conversationFile);
    var dispatcher = new ConsoleCommandDispatcher(new CommandRegistry(switchable).commands());
    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;

    try (var input =
            new ByteArrayInputStream(
                "First question\n/llm OPENAI\nSecond question\n/exit\n"
                    .getBytes(StandardCharsets.UTF_8));
        var output = new PrintStream(new ByteArrayOutputStream())) {
      System.setIn(input);
      System.setOut(output);
      new ConsoleChat(new ChatService(agent), config, dispatcher, repository).start();
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }

    assertEquals(4, repository.load().messages().size());
    assertEquals(2, ollama.lastPromptSize);
    assertEquals(4, openAi.lastPromptSize);
    assertEquals(LlmProvider.OPENAI, switchable.activeProvider());
  }

  private static final class ScriptedClient implements LlmClient {
    private final String model;
    private final String answer;
    private int lastPromptSize;

    private ScriptedClient(String model, String answer) {
      this.model = model;
      this.answer = answer;
    }

    @Override
    public LlmResponse chat(Prompt prompt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {
      lastPromptSize = prompt.messages().size();
      consumer.accept(
          new ChatChunk(
              "{\"type\":\"model_reply\",\"content\":\"" + answer + "\"}",
              ChatChunkType.CONTENT,
              true));
      return new StreamingResult(model, new TokenUsage(1, 1));
    }
  }
}
