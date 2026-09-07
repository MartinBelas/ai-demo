package ai.demo.client.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.EmbeddingProvider;
import ai.demo.config.RagConfig;
import ai.demo.exception.RagException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingClientTest {

  private HttpTransport transport;
  private OllamaEmbeddingClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    RagConfig config =
        new RagConfig(
            true,
            EmbeddingProvider.OLLAMA,
            "http://localhost:11434",
            "embeddinggemma",
            null,
            200000,
            20,
            400000,
            1200,
            6000,
            4000,
            5,
            0.2);
    client = new OllamaEmbeddingClient(transport, new ObjectMapper(), config);
  }

  @Test
  void shouldMapEmbeddingVector() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
        {"embeddings":[[0.1,0.2,0.3]]}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    var embedding = client.embed("hello");

    assertEquals(3, embedding.values().size());
  }

  @Test
  void shouldRejectNon200Status() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(500);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    assertThrows(RagException.class, () -> client.embed("hello"));
  }

  @Test
  void shouldRejectMissingVectorCount() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
        {"embeddings":[]}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    assertThrows(RagException.class, () -> client.embed("hello"));
  }
}
