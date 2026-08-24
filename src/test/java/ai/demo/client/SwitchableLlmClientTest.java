package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import ai.demo.config.LlmProvider;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SwitchableLlmClientTest {

  @Test
  void shouldCreateAlternateProviderLazilyAndSwitch() {
    LlmClient ollama = mock(LlmClient.class);
    LlmClient openAi = mock(LlmClient.class);
    AtomicInteger openAiCreations = new AtomicInteger();
    var client =
        new SwitchableLlmClient(
            LlmProvider.OLLAMA,
            Map.of(
                LlmProvider.OLLAMA,
                () -> ollama,
                LlmProvider.OPENAI,
                () -> {
                  openAiCreations.incrementAndGet();
                  return openAi;
                }));

    assertEquals(0, openAiCreations.get());
    client.switchTo(LlmProvider.OPENAI);

    assertEquals(LlmProvider.OPENAI, client.activeProvider());
    assertEquals(1, openAiCreations.get());
  }

  @Test
  void shouldKeepCurrentProviderWhenSwitchFails() {
    LlmClient ollama = mock(LlmClient.class);
    var client =
        new SwitchableLlmClient(
            LlmProvider.OLLAMA,
            Map.of(
                LlmProvider.OLLAMA,
                () -> ollama,
                LlmProvider.OPENAI,
                () -> {
                  throw new IllegalStateException("Missing API key");
                }));

    assertThrows(IllegalStateException.class, () -> client.switchTo(LlmProvider.OPENAI));
    assertEquals(LlmProvider.OLLAMA, client.activeProvider());
  }
}
