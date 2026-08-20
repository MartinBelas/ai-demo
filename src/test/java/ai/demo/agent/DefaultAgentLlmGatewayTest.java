package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.client.TokenUsage;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultAgentLlmGatewayTest {

  @Test
  void shouldComposePromptAndCallLlmClient() {

    LlmClient llmClient = mock(LlmClient.class);
    PromptComposer promptComposer = mock(PromptComposer.class);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));

    Map<String, String> variables = Map.of("tools", "calculator");

    Prompt prompt = new Prompt(List.of(ChatMessage.user("Hello")));

    LlmResponse expected =
        new LlmResponse(
            """
                        {
                          "type": "model_reply",
                          "content": "Hello"
                        }
                        """,
            "test-model",
            new TokenUsage(5, 3));

    when(promptComposer.compose(conversation, variables)).thenReturn(prompt);

    when(llmClient.chat(prompt)).thenReturn(expected);

    AgentLlmGateway gateway = new DefaultAgentLlmGateway(llmClient, promptComposer);

    LlmResponse result = gateway.request(conversation, variables);

    assertEquals(expected, result);

    verify(promptComposer).compose(conversation, variables);

    verify(llmClient).chat(prompt);
  }
}
