package ai.demo;

import ai.demo.client.LlmClient;
import ai.demo.client.LoggingLlmClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.http.JdkHttpTransport;
import ai.demo.client.ollama.OllamaClient;
import ai.demo.config.AppConfig;
import ai.demo.config.AppConfigLoader;
import ai.demo.console.ConsoleChat;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.prompt.PromptComposer;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point for AI Demo. Initializes configuration, HTTP client, and starts the
 * console chat interface.
 */
public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    int exitCode = new App().run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Run the application. Returns 0 on success or a non-zero exit code on error. Extracted for
   * testability.
   */
  public int run(String[] args) {
    // Configuration
    AppConfigLoader configLoader = new AppConfigLoader();
    AppConfig config;

    try {
      config = configLoader.load();
    } catch (ConfigurationException | IOException e) {
      log.error("Configuration error: {}", e.getMessage(), e);
      return 1;
    }

    // Infrastructure
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    ObjectMapper objectMapper = new ObjectMapper();
    PromptComposer promptComposer = new PromptComposer();

    // Clients
    final HttpTransport httpTransport = new JdkHttpTransport(httpClient);
    LlmClient llmClient =
        new LoggingLlmClient(new OllamaClient(config, httpTransport, objectMapper));

    // Services
    ChatService chatService = new ChatService(llmClient, promptComposer);

    // UI
    ConsoleChat consoleChat = new ConsoleChat(chatService, config);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  httpClient.shutdownNow();
                  log.info("Shutdown complete.");
                }));

    try {
      consoleChat.start();
      return 0;
    } catch (LlmCommunicationException e) {
      log.error("LLM communication error: {}", e.getMessage(), e);
      return 2;
    } catch (Exception e) {
      log.error("Unexpected error: {}", e.getMessage(), e);
      return 99;
    }
  }
}
