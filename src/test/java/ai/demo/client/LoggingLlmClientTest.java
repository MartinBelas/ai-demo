package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.prompt.Prompt;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoggingLlmClientTest {

  @Test
  void shouldLogAndReturnSuccessfulResponse() {

    LlmClient mockClient = mock(LlmClient.class);
    LlmResponse response = new LlmResponse("Test response", "test-model");

    when(mockClient.chat(any(Prompt.class))).thenReturn(response);

    LoggingLlmClient loggingClient = new LoggingLlmClient(mockClient);

    Prompt prompt = new Prompt(List.of(ChatMessage.user("Test question")));

    LlmResponse result = loggingClient.chat(prompt);

    assertEquals("Test response", result.text());
    assertEquals("test-model", result.model());
  }

  @Test
  void shouldLogAndRethrowLlmException() {

    LlmClient mockClient = mock(LlmClient.class);
    LlmCommunicationException exception = new LlmCommunicationException("Connection failed");

    when(mockClient.chat(any(Prompt.class))).thenThrow(exception);

    LoggingLlmClient loggingClient = new LoggingLlmClient(mockClient);

    Prompt prompt = new Prompt(List.of(ChatMessage.user("Test question")));

    LlmCommunicationException thrown =
        assertThrows(LlmCommunicationException.class, () -> loggingClient.chat(prompt));

    assertEquals("Connection failed", thrown.getMessage());
  }

  @Test
  void shouldLogAndRethrowRuntimeException() {

    LlmClient mockClient = mock(LlmClient.class);
    RuntimeException exception = new RuntimeException("Unexpected error");

    when(mockClient.chat(any(Prompt.class))).thenThrow(exception);

    LoggingLlmClient loggingClient = new LoggingLlmClient(mockClient);

    Prompt prompt = new Prompt(List.of(ChatMessage.user("Test question")));

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> loggingClient.chat(prompt));

    assertEquals("Unexpected error", thrown.getMessage());
  }
}
