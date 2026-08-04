package ai.demo.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import org.junit.jupiter.api.Test;

class PromptComposerTest {

  private final PromptComposer promptComposer = new PromptComposer();

  @Test
  void shouldCreatePromptFromConversation() {

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hello"));

    var prompt = promptComposer.compose(conversation);

    assertEquals(1, prompt.messages().size());
    assertEquals(Role.USER, prompt.messages().getFirst().role());
    assertEquals("Hello", prompt.messages().getFirst().content());
  }
}
