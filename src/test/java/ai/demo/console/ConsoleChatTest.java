package ai.demo.console;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import ai.demo.config.AppConfig;
import ai.demo.service.ChatService;
import org.junit.jupiter.api.Test;

/**
 * Unit test for ConsoleChat. Verifies basic instantiation. Exception handling during LLM
 * communication is tested via AppRunTest integration test.
 */
class ConsoleChatTest {

  @Test
  void shouldInstantiateWithValidDependencies() {
    var mockService = mock(ChatService.class);
    AppConfig config = new AppConfig("http://localhost:11434", "test-model", 0.7, 10, 100, 1.2);

    var console = new ConsoleChat(mockService, config);

    assertNotNull(console);
  }
}
