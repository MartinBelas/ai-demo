package ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

  @Test
  void shouldReturnChatResponse() {

    var llmClient = mock(LlmClient.class);

    when(llmClient.chat(any(Conversation.class)))
        .thenReturn(new LlmResponse("Test response", "test-model"));

    var conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    var chatService = new ChatService(llmClient);
    ChatResponse response = chatService.ask(conversation);

    assertEquals("Test response", response.answer());
    assertEquals("test-model", response.model());
    assertTrue(response.durationMs() >= 0);
  }
}
