package ai.demo.console.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmProviderSelector;
import ai.demo.config.LlmProvider;
import ai.demo.console.ConsoleContext;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class ProviderCommandTest {

  private final LlmProviderSelector selector = mock(LlmProviderSelector.class);
  private final ProviderCommand command = new ProviderCommand(selector);
  private final ConsoleContext context = new ConsoleContext(new Conversation());

  @Test
  void shouldReportStatusAndSwitchProvider() {
    when(selector.activeProvider()).thenReturn(LlmProvider.OLLAMA);

    CommandResult status = command.execute(context, new String[] {"STATUS"});
    CommandResult switched = command.execute(context, new String[] {"OPENAI"});

    assertEquals("Active LLM provider: OLLAMA", status.message());
    assertEquals(CommandStatus.SUCCESS, switched.status());
    verify(selector).switchTo(LlmProvider.OPENAI);
  }

  @Test
  void shouldReturnFailureWithoutChangingProviderWhenSwitchFails() {
    org.mockito.Mockito.doThrow(new IllegalStateException("Missing API key"))
        .when(selector)
        .switchTo(LlmProvider.OPENAI);

    CommandResult result = command.execute(context, new String[] {"OPENAI"});

    assertEquals(CommandStatus.FAILURE, result.status());
    assertEquals("Unable to switch LLM provider: Missing API key", result.message());
  }
}
