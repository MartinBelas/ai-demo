package ai.demo.client.gemini;

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

class GeminiEmbeddingClientTest {

  private HttpTransport transport;
  private GeminiEmbeddingClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    RagConfig config =
        new RagConfig(
            true,
            EmbeddingProvider.GEMINI,
            "https://generativelanguage.googleapis.com/v1beta",
            "text-embedding-004",
            "GEMINI_API_KEY",
            200000,
            20,
            400000,
            1200,
            6000,
            4000,
            5,
            0.2);
    client = new GeminiEmbeddingClient(config, "secret", transport, new ObjectMapper());
  }

  @Test
  void shouldMapEmbeddingVector() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
        {"embedding":{"values":[0.1,0.2,0.3]}}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    var embedding = client.embed("hello");

    assertEquals(3, embedding.values().size());
  }

  @Test
  void shouldRejectNonSuccessStatus() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(403);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    assertThrows(RagException.class, () -> client.embed("hello"));
  }

  @Test
  void shouldRejectMissingVectorInResponse() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
        {"embedding":{}}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    assertThrows(RagException.class, () -> client.embed("hello"));
  }
}
