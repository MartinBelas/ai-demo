package ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import ai.demo.prompt.template.PromptTemplateLoader;
import ai.demo.prompt.template.PromptTemplateRenderer;
import ai.demo.prompt.template.PromptTemplateType;
import ai.demo.prompt.template.SystemPromptProvider;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

  @Test
  void shouldReturnChatResponse() {

    LlmClient llmClient = mock(LlmClient.class);

    when(llmClient.chat(any(Prompt.class)))
        .thenReturn(new LlmResponse("Test response", "test-model"));

    PromptComposer promptComposer =
        new PromptComposer(
            new SystemPromptProvider(
                PromptTemplateType.CHAT, new PromptTemplateLoader(), new PromptTemplateRenderer()));

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    ChatService chatService = new ChatService(llmClient, promptComposer);

    ChatResponse response = chatService.ask(conversation);

    assertEquals("Test response", response.answer());
    assertEquals("test-model", response.model());
    assertTrue(response.durationInSeconds() >= 0);
  }

  @Test
  void shouldRejectEmptyConversation() {

    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer =
        new PromptComposer(
            new SystemPromptProvider(
                PromptTemplateType.CHAT, new PromptTemplateLoader(), new PromptTemplateRenderer()));

    Conversation conversation = new Conversation();

    ChatService chatService = new ChatService(llmClient, promptComposer);

    assertThrows(IllegalStateException.class, () -> chatService.ask(conversation));

    verifyNoInteractions(llmClient);
  }
}
