package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import org.junit.jupiter.api.Test;

class LoggingLlmClientTest {

  @Test
  void shouldLogAndReturnSuccessfulResponse() {

    var mockClient = mock(LlmClient.class);
    var response = new LlmResponse("Test response", "test-model");
    when(mockClient.chat(any(Conversation.class))).thenReturn(response);

    var loggingClient = new LoggingLlmClient(mockClient);
    var conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    LlmResponse result = loggingClient.chat(conversation);

    assertEquals("Test response", result.text());
    assertEquals("test-model", result.model());
  }

  @Test
  void shouldLogAndRethrowLlmException() {

    var mockClient = mock(LlmClient.class);
    var exception = new LlmCommunicationException("Connection failed");
    when(mockClient.chat(any(Conversation.class))).thenThrow(exception);

    var loggingClient = new LoggingLlmClient(mockClient);
    var conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    LlmCommunicationException thrown =
        assertThrows(LlmCommunicationException.class, () -> loggingClient.chat(conversation));

    assertEquals("Connection failed", thrown.getMessage());
  }

  @Test
  void shouldLogAndRethrowRuntimeException() {

    var mockClient = mock(LlmClient.class);
    var exception = new RuntimeException("Unexpected error");
    when(mockClient.chat(any(Conversation.class))).thenThrow(exception);

    var loggingClient = new LoggingLlmClient(mockClient);
    var conversation = new Conversation();
    conversation.add(ChatMessage.user("Test question"));

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> loggingClient.chat(conversation));

    assertEquals("Unexpected error", thrown.getMessage());
  }
}
