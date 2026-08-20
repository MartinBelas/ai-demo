package ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentResult;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

  @Test
  void shouldReturnChatResponse() {

    Agent agent = mock(Agent.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    when(agent.execute(eq(conversation), any()))
        .thenReturn(new AgentResult("Test response", "test-model", new TokenUsage(10, 20)));

    ChatService chatService = new ChatService(agent);

    ChatResponse response = chatService.ask(conversation);

    assertEquals("Test response", response.answer());
    assertEquals("test-model", response.model());
    assertEquals(new TokenUsage(10, 20), response.tokenUsage());
    assertTrue(response.durationMs() >= 0);

    verify(agent).execute(eq(conversation), any());
  }

  @Test
  void shouldRejectEmptyConversation() {

    Agent agent = mock(Agent.class);

    Conversation conversation = new Conversation();

    ChatService chatService = new ChatService(agent);

    assertThrows(IllegalStateException.class, () -> chatService.ask(conversation));

    verifyNoInteractions(agent);
  }
}
