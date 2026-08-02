package ai.demo.client.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.LlmResponse;
import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

  private HttpTransport httpTransport;
  private OllamaClient ollamaClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    AppConfig config = new AppConfig("http://localhost:11434", "qwen3:4b", 0.7, 100, 4096);
    httpTransport = mock(HttpTransport.class);
    ollamaClient = new OllamaClient(config, httpTransport, objectMapper);
  }

  @Test
  void shouldReturnLlmResponseWhenOllamaReturnsSuccess() throws Exception {

    String jsonResponse =
        """
                {
                  "model": "qwen3:4b",
                  "message": {
                    "role": "assistant",
                    "content": "Hello"
                  }
                }
                """;

    HttpResponse<String> httpResponse = mock();
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(jsonResponse);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hi"));

    LlmResponse response = ollamaClient.chat(conversation);

    assertEquals("Hello", response.text());
    assertEquals("qwen3:4b", response.model());
  }

  @Test
  void shouldThrowExceptionWhenOllamaReturnsHttpError() throws Exception {

    HttpResponse<String> httpResponse = mock();
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hi"));

    assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(conversation));
  }

  @Test
  void shouldThrowExceptionWhenCommunicationFails() throws Exception {

    when(httpTransport.send(any())).thenThrow(new IOException("Connection refused"));

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hi"));

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(conversation));

    assertEquals("Failed to communicate with Ollama", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenResponseIsInvalidJson() throws Exception {

    HttpResponse<String> httpResponse = mock();
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("{ invalid json }");
    when(httpTransport.send(any())).thenReturn(httpResponse);

    Conversation conversation = new Conversation();
    conversation.add(new ChatMessage(Role.USER, "Hi"));

    LlmCommunicationException exception =
        assertThrows(LlmCommunicationException.class, () -> ollamaClient.chat(conversation));

    assertEquals("Failed to communicate with Ollama", exception.getMessage());
  }
}
