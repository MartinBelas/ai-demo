package ai.demo.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import ai.demo.prompt.template.PromptTemplateLoader;
import ai.demo.prompt.template.PromptTemplateRenderer;
import ai.demo.prompt.template.PromptTemplateType;
import ai.demo.prompt.template.SystemPromptProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptComposerTest {

  private final PromptComposer promptComposer =
      new PromptComposer(
          new SystemPromptProvider(
              PromptTemplateType.AGENT, new PromptTemplateLoader(), new PromptTemplateRenderer()),
          Map.of("systemMessage", "Be helpful."));

  @Test
  void shouldAddSystemPromptBeforeConversation() {

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    var prompt = promptComposer.compose(conversation, Map.of("tools", "calculator: Calculates."));

    assertEquals(2, prompt.messages().size());

    assertEquals(Role.SYSTEM, prompt.messages().getFirst().role());
    String systemPrompt = prompt.messages().getFirst().content();
    assertTrue(systemPrompt.startsWith("Be helpful."));
    assertTrue(systemPrompt.contains("calculator: Calculates."));
    assertFalse(systemPrompt.contains("{{"));

    assertEquals(Role.USER, prompt.messages().get(1).role());
    assertEquals("Hello", prompt.messages().get(1).content());
  }

  @Test
  void shouldNotAddSystemPromptWhenBlank() {

    SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);

    when(systemPromptProvider.getSystemPrompt(Map.of())).thenReturn("");

    PromptComposer blankPromptComposer = new PromptComposer(systemPromptProvider);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    var prompt = blankPromptComposer.compose(conversation);

    assertEquals(1, prompt.messages().size());

    assertEquals(Role.USER, prompt.messages().getFirst().role());

    assertEquals("Hello", prompt.messages().getFirst().content());
  }
}
