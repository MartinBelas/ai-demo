package ai.demo;

import ai.demo.client.LlmClient;
import ai.demo.client.OllamaClient;
import ai.demo.config.AppConfig;
import ai.demo.console.ConsoleChat;
import ai.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;

/**
 * Main application entry point for AI Demo.
 * Initializes configuration, HTTP client, and starts the console chat interface.
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
    final AppConfig config = AppConfig.load();

    // Infrastructure
    final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    final ObjectMapper objectMapper = new ObjectMapper();

    // Clients
    final LlmClient llmClient = new OllamaClient(httpClient, objectMapper, config);

    // Services
    final ChatService chatService = new ChatService(llmClient);

    final ConsoleChat consoleChat = new ConsoleChat(chatService, config);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      httpClient.shutdownNow();
      System.out.println("Shutdown complete.");
    }));

    consoleChat.start();
  }
}
