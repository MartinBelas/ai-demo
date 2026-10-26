package ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.demo.client.http.HttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.EmbeddingProvider;
import ai.demo.config.LlmProvider;
import ai.demo.config.OllamaConfig;
import ai.demo.config.TestAppConfig;
import ai.demo.exception.ConfigurationException;
import ai.demo.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** Startup wiring of the shared RAG service. */
class AppRagWiringTest {

  private final HttpTransport transport = mock(HttpTransport.class);
  private final UnaryOperator<String> environment = key -> null;

  @Test
  void shouldNotCreateRagServiceWhenRagIsDisabled() {
    AppConfig config = config(TestAppConfig.builder(LlmProvider.OLLAMA));

    assertNull(createRagService(config));
  }

  @Test
  void shouldVerifyBundledDocumentsWithoutCallingEmbeddingProvider() {
    AppConfig config =
        config(
            TestAppConfig.builder(LlmProvider.OLLAMA)
                .rag(TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 200000)));

    RagService service = createRagService(config);

    assertNotNull(service);
    verifyNoInteractions(transport);
  }

  @Test
  void shouldFailStartupWhenBundledDocumentsExceedConfiguredLimits() {
    AppConfig config =
        config(
            TestAppConfig.builder(LlmProvider.OLLAMA)
                .rag(TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 10)));

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> createRagService(config));

    assertTrue(exception.getMessage().startsWith("Invalid RAG documents"));
  }

  @Test
  void shouldCloseHttpClientWhenServerStartupFailsOnInvalidRagDocuments() {
    HttpClient httpClient = mock(HttpClient.class);
    App app =
        new App() {
          @Override
          HttpClient createHttpClient() {
            return httpClient;
          }
        };
    AppConfig config =
        config(
            TestAppConfig.builder(LlmProvider.OLLAMA)
                .rag(TestAppConfig.enabledRag(EmbeddingProvider.OLLAMA, 10)));

    int exitCode = app.startServer(config, environment);

    assertEquals(1, exitCode);
    verify(httpClient).close();
  }

  @Test
  void shouldCloseHttpClientWhenServerStartupFailsWithUnexpectedError() {
    HttpClient httpClient = mock(HttpClient.class);
    App app =
        new App() {
          @Override
          HttpClient createHttpClient() {
            return httpClient;
          }
        };
    AppConfig config = config(TestAppConfig.builder(LlmProvider.OLLAMA));
    UnaryOperator<String> failingEnvironment =
        key -> {
          throw new UnsupportedOperationException("environment unavailable");
        };

    assertThrows(
        UnsupportedOperationException.class, () -> app.startServer(config, failingEnvironment));

    verify(httpClient).close();
  }

  private RagService createRagService(AppConfig config) {
    return new App().createRagService(config, transport, new ObjectMapper(), environment);
  }

  private static AppConfig config(TestAppConfig builder) {
    return builder
        .ollama(new OllamaConfig("qwen3:4b", "http://localhost:11434", 4096, 1.2))
        .build();
  }
}
