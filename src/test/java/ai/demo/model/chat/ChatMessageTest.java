package ai.demo.model.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatMessageTest {

  @Test
  void shouldCreateUserMessage() {

    var message = ChatMessage.user("Hello");

    assertEquals(Role.USER, message.role());
    assertEquals("Hello", message.content());
  }

  @Test
  void shouldCreateAssistantMessage() {

    var message = ChatMessage.assistant("Hi");

    assertEquals(Role.ASSISTANT, message.role());
    assertEquals("Hi", message.content());
  }

  @Test
  void shouldCreateSystemMessage() {

    var message = ChatMessage.system("You are helpful.");

    assertEquals(Role.SYSTEM, message.role());
    assertEquals("You are helpful.", message.content());
  }
}
