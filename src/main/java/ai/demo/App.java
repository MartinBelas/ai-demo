package ai.demo;

import ai.demo.client.LlmClient;
import ai.demo.client.LoggingLlmClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.http.JdkHttpTransport;
import ai.demo.client.ollama.OllamaClient;
import ai.demo.config.AppConfig;
import ai.demo.config.AppConfigLoader;
import ai.demo.console.ConsoleChat;
import ai.demo.console.command.CommandRegistry;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.prompt.PromptComposer;
import ai.demo.prompt.template.PromptTemplateLoader;
import ai.demo.prompt.template.PromptTemplateRenderer;
import ai.demo.prompt.template.PromptTemplateType;
import ai.demo.prompt.template.SystemPromptProvider;
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
    int exitCode = new App().run();
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Run the application. Returns 0 on success or a non-zero exit code on error. Extracted for
   * testability.
   */
  public int run() {

    AppConfig config;

    try {
      config = loadConfig();
    } catch (Exception e) {
      log.error("Configuration error: {}", e.getMessage(), e);
      return 1;
    }

    HttpClient httpClient = createHttpClient();

    PromptComposer composer = createPromptComposer();

    LlmClient llmClient = createLlmClient(config, httpClient);

    ChatService chatService = new ChatService(llmClient, composer);

    ConsoleCommandDispatcher dispatcher =
        new ConsoleCommandDispatcher(new CommandRegistry().commands());

    ConsoleChat consoleChat = new ConsoleChat(chatService, config, dispatcher);

    addShutdownHook(httpClient);

    return startConsole(consoleChat);
  }

  private AppConfig loadConfig() throws IOException {
    AppConfigLoader loader = new AppConfigLoader();
    return loader.load();
  }

  private HttpClient createHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private PromptComposer createPromptComposer() {
    PromptTemplateLoader loader = new PromptTemplateLoader();
    PromptTemplateRenderer renderer = new PromptTemplateRenderer();
    SystemPromptProvider provider =
        new SystemPromptProvider(PromptTemplateType.CHAT, loader, renderer);

    return new PromptComposer(provider);
  }

  private LlmClient createLlmClient(AppConfig config, HttpClient httpClient) {
    HttpTransport transport = new JdkHttpTransport(httpClient);
    ObjectMapper mapper = new ObjectMapper();
    return new LoggingLlmClient(new OllamaClient(config, transport, mapper));
  }

  private void addShutdownHook(HttpClient httpClient) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  httpClient.shutdownNow();
                  log.info("Shutdown complete.");
                }));
  }

  private int startConsole(ConsoleChat consoleChat) {
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
