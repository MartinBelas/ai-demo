package ai.demo;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.config.AppConfig;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import ai.demo.service.ChatService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppTest {

  @Test
  public void testChatService() {

    LlmClient mockClient = mock(LlmClient.class);
    when(mockClient.chat(any(Conversation.class)))
            .thenReturn(
                    new LlmResponse(
                            "Test response",
                            "test-model"
                    )
            );

    ChatService chatService = new ChatService(mockClient);
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    ChatResponse response = chatService.ask(conversation);

    assertEquals("Test response", response.answer());
    assertEquals("test-model", response.model());
    assertTrue(response.durationMs() >= 0);
  }

  @Test
  public void testAppConfigRecord() {

    AppConfig config = new AppConfig("http://localhost:11434", "qwen3:4b", 0.5, 2000, 4096);

    assertEquals("http://localhost:11434", config.baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.5, config.temperature());
    assertEquals(2000, config.numPredict());
    assertEquals(4096, config.numCtx());
  }

  @Test
  public void testLlmResponseRecord() {

    LlmResponse response =
            new LlmResponse(
                    "Test text",
                    "test-model"
            );

    assertEquals("Test text", response.text());
    assertEquals("test-model", response.model());
  }

  @Test
  public void testLlmResponseValidation() {
    assertThrows(IllegalArgumentException.class, () -> new LlmResponse(null, "model"));
    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", null));
    assertThrows(IllegalArgumentException.class, () -> new LlmResponse("text", ""));
  }

  @Test
  public void testChatResponseValidation() {
    assertThrows(IllegalArgumentException.class, () -> new ai.demo.model.chat.ChatResponse(null, "model", 100));
    assertThrows(IllegalArgumentException.class, () -> new ai.demo.model.chat.ChatResponse("answer", null, 100));
    assertThrows(IllegalArgumentException.class, () -> new ai.demo.model.chat.ChatResponse("answer", "", 100));
    assertThrows(IllegalArgumentException.class, () -> new ai.demo.model.chat.ChatResponse("answer", "model", -1));
  }

  @Test
  public void testAppConfigValidation() {
    assertThrows(IllegalArgumentException.class, () -> new AppConfig(null, "model", 0.5, 100, 100));
    assertThrows(IllegalArgumentException.class, () -> new AppConfig("url", null, 0.5, 100, 100));
    assertThrows(
        IllegalArgumentException.class, () -> new AppConfig("url", "model", -1.0, 100, 100));
    assertThrows(
        IllegalArgumentException.class, () -> new AppConfig("url", "model", 3.0, 100, 100));
    assertThrows(IllegalArgumentException.class, () -> new AppConfig("url", "model", 0.5, 0, 100));
    assertThrows(IllegalArgumentException.class, () -> new AppConfig("url", "model", 0.5, 100, 0));
  }

  @Test
  void testConversation() {

    Conversation conversation = new Conversation();

    conversation.add(ChatMessage.user("Hello"));
    conversation.add(ChatMessage.assistant("Hi"));

    assertEquals(2, conversation.messages().size());

    assertEquals(
            Role.USER,
            conversation.messages().getFirst().role());

    assertEquals(
            Role.ASSISTANT,
            conversation.messages().getLast().role());
  }
}
