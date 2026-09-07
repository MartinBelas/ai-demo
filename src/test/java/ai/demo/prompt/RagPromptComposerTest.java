package ai.demo.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import ai.demo.model.rag.RagSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPromptComposerTest {

  private final RagPromptComposer composer = new RagPromptComposer();

  @Test
  void shouldInsertSystemInstructionWithSourcesBeforeQuestion() {
    Conversation original = new Conversation();
    original.add(ChatMessage.user("Hi"));
    original.add(ChatMessage.assistant("Hello"));
    original.add(ChatMessage.user("What is the calculator tool?"));
    RagSource source =
        new RagSource("agents.md:3", "agents.md", 3, "The calculator tool evaluates arithmetic.");

    Conversation composed = composer.compose(original, List.of(source));

    assertEquals(4, composed.messages().size());
    ChatMessage question = composed.messages().getLast();
    assertEquals(Role.USER, question.role());
    assertEquals("What is the calculator tool?", question.content());

    ChatMessage instructions = composed.messages().get(composed.messages().size() - 2);
    assertEquals(Role.SYSTEM, instructions.role());
    assertTrue(instructions.content().contains("The calculator tool evaluates arithmetic."));
    assertTrue(instructions.content().contains("agents.md:3"));

    assertEquals("Hi", composed.messages().get(0).content());
    assertEquals("Hello", composed.messages().get(1).content());
  }

  @Test
  void shouldComposeWithEmptySourceList() {
    Conversation original = new Conversation();
    original.add(ChatMessage.user("What is this project?"));

    Conversation composed = composer.compose(original, List.of());

    assertEquals(2, composed.messages().size());
    assertEquals(Role.SYSTEM, composed.messages().getFirst().role());
    assertEquals("What is this project?", composed.messages().getLast().content());
  }

  @Test
  void shouldNotMutateOriginalConversation() {
    Conversation original = new Conversation();
    original.add(ChatMessage.user("What is this project?"));

    composer.compose(original, List.of());

    assertEquals(1, original.messages().size());
    assertEquals("What is this project?", original.messages().getLast().content());
  }
}
