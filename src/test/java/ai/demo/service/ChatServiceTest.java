package ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentResult;
import ai.demo.client.LlmClient;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.prompt.PromptComposer;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

  @Test
  void shouldReturnChatResponse() {

    Agent agent = mock(Agent.class);
    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer = mock(PromptComposer.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    when(agent.execute(conversation))
        .thenReturn(new AgentResult("Test response", "test-model", new TokenUsage(10, 20)));

    ChatService chatService = new ChatService(agent, llmClient, promptComposer);

    ChatResponse response = chatService.ask(conversation);

    assertEquals("Test response", response.answer());
    assertEquals("test-model", response.model());
    assertTrue(response.durationInSeconds() >= 0);

    verify(agent).execute(conversation);
    verifyNoInteractions(llmClient);
    verifyNoInteractions(promptComposer);
  }

  @Test
  void shouldRejectEmptyConversation() {

    Agent agent = mock(Agent.class);
    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer = mock(PromptComposer.class);

    Conversation conversation = new Conversation();

    ChatService chatService = new ChatService(agent, llmClient, promptComposer);

    assertThrows(IllegalStateException.class, () -> chatService.ask(conversation));

    verifyNoInteractions(agent);
    verifyNoInteractions(llmClient);
    verifyNoInteractions(promptComposer);
  }
}
