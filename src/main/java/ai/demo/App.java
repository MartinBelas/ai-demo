package ai.demo;

import ai.demo.client.LlmClient;
import ai.demo.client.LoggingLlmClient;
import ai.demo.client.ollama.OllamaClient;
import ai.demo.config.AppConfig;
import ai.demo.config.AppConfigLoader;
import ai.demo.console.ConsoleChat;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Main application entry point for AI Demo. Initializes configuration, HTTP client, and starts the
 * console chat interface.
 */
public class App {

  /**
   * Main method to start the application.
   *
   * @param args command line arguments (not used)
   * @throws IOException if configuration loading fails
   */
  public static void main(String[] args) throws IOException {

    // Configuration
    AppConfigLoader configLoader = new AppConfigLoader();
    AppConfig config = configLoader.load();

    // Infrastructure
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    ObjectMapper objectMapper = new ObjectMapper();

    // Clients
    LlmClient llmClient = new LoggingLlmClient(new OllamaClient(config, httpClient, objectMapper));

    // Services
    ChatService chatService = new ChatService(llmClient);

    // UI
    ConsoleChat consoleChat = new ConsoleChat(chatService, config);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  httpClient.shutdownNow();
                  System.out.println("Shutdown complete.");
                }));

    consoleChat.start();
  }
}
