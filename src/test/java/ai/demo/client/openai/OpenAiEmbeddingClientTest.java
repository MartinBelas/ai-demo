package ai.demo.client.openai;

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

class OpenAiEmbeddingClientTest {

  private HttpTransport transport;
  private OpenAiEmbeddingClient client;

  @BeforeEach
  void setUp() {
    transport = mock(HttpTransport.class);
    RagConfig config =
        new RagConfig(
            true,
            EmbeddingProvider.OPENAI,
            "https://api.openai.com/v1",
            "text-embedding-3-small",
            "OPENAI_API_KEY",
            200000,
            20,
            400000,
            1200,
            6000,
            4000,
            5,
            0.2);
    client = new OpenAiEmbeddingClient(config, "secret", transport, new ObjectMapper());
  }

  @Test
  void shouldMapEmbeddingVector() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
        {"data":[{"embedding":[0.1,0.2]}],"model":"text-embedding-3-small"}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    var embedding = client.embed("hello");

    assertEquals(2, embedding.values().size());
  }

  @Test
  void shouldRejectNonSuccessStatus() throws Exception {
    HttpResponse<String> response = mock();
    when(response.statusCode()).thenReturn(401);
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
        {"data":[]}
        """);
    when(transport.send(any(HttpRequest.class))).thenReturn(response);

    assertThrows(RagException.class, () -> client.embed("hello"));
  }

  @Test
  void shouldRejectBlankApiKey() {
    RagConfig config =
        new RagConfig(
            true,
            EmbeddingProvider.OPENAI,
            "https://api.openai.com/v1",
            "text-embedding-3-small",
            "OPENAI_API_KEY",
            200000,
            20,
            400000,
            1200,
            6000,
            4000,
            5,
            0.2);
    ObjectMapper mapper = new ObjectMapper();

    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenAiEmbeddingClient(config, " ", transport, mapper));
  }
}
