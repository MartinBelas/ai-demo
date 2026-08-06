package ai.demo.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import ai.demo.prompt.template.PromptTemplateLoader;
import ai.demo.prompt.template.PromptTemplateRenderer;
import ai.demo.prompt.template.PromptTemplateType;
import ai.demo.prompt.template.SystemPromptProvider;
import org.junit.jupiter.api.Test;

class PromptComposerTest {

  private final PromptComposer promptComposer =
      new PromptComposer(
          new SystemPromptProvider(
              PromptTemplateType.CHAT, new PromptTemplateLoader(), new PromptTemplateRenderer()));

  @Test
  void shouldAddSystemPromptBeforeConversation() {

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    var prompt = promptComposer.compose(conversation);

    assertEquals(2, prompt.messages().size());

    assertEquals(Role.SYSTEM, prompt.messages().get(0).role());
    assertEquals(
        "You are a helpful assistant.\n\n{{conversation}}", prompt.messages().get(0).content());

    assertEquals(Role.USER, prompt.messages().get(1).role());
    assertEquals("Hello", prompt.messages().get(1).content());
  }

  @Test
  void shouldNotAddSystemPromptWhenBlank() {

    SystemPromptProvider mockProvider =
        new SystemPromptProvider(
            PromptTemplateType.CHAT, new PromptTemplateLoader(), new PromptTemplateRenderer()) {

          @Override
          public String getSystemPrompt() {
            return "";
          }
        };

    PromptComposer blankPromptComposer = new PromptComposer(mockProvider);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    var prompt = blankPromptComposer.compose(conversation);

    assertEquals(1, prompt.messages().size());

    assertEquals(Role.USER, prompt.messages().getFirst().role());
    assertEquals("Hello", prompt.messages().getFirst().content());
  }
}
