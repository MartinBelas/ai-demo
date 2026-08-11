package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import ai.demo.config.AppConfig;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.persistence.ConversationRepository;
import ai.demo.service.ChatService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConsoleChatTest {

  @Test
  void shouldInstantiateWithValidDependencies() {
    var mockService = mock(ChatService.class);
    var mockDispatcher = mock(ConsoleCommandDispatcher.class);
    var mockRepo = mock(ConversationRepository.class);

    AppConfig config =
        new AppConfig(
            "http://localhost:11434", "test-model", 0.7, 10, 100, 1.2, Path.of("conv.json"));

    var console = new ConsoleChat(mockService, config, mockDispatcher, mockRepo);

    assertNotNull(console);
  }
}
