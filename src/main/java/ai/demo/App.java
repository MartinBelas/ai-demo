package ai.demo;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentLlmGateway;
import ai.demo.agent.DefaultAgentLlmGateway;
import ai.demo.agent.ToolCallingAgent;
import ai.demo.agent.tool.CalculatorTool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
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
import ai.demo.persistence.ConversationRepository;
import ai.demo.persistence.FileConversationRepository;
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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    int exitCode = new App().run();

    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  public int run() {

    AppConfig config;

    try {
      config = loadConfig();
    } catch (Exception e) {
      log.error("Configuration error: {}", e.getMessage(), e);
      return 1;
    }

    HttpClient httpClient = createHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    LlmClient llmClient = createLlmClient(config, httpClient, objectMapper);

    PromptComposer chatPromptComposer = createPromptComposer(PromptTemplateType.CHAT);

    PromptComposer agentPromptComposer = createPromptComposer(PromptTemplateType.AGENT);

    ChatService chatService = new ChatService(llmClient, chatPromptComposer);

    Agent agent = createAgent(llmClient, agentPromptComposer, objectMapper);

    ConsoleCommandDispatcher dispatcher =
        new ConsoleCommandDispatcher(new CommandRegistry().commands());

    ConversationRepository conversationRepository =
        new FileConversationRepository(config.conversationFile(), objectMapper);

    ConsoleChat consoleChat =
        new ConsoleChat(chatService, config, dispatcher, conversationRepository);

    addShutdownHook(httpClient);

    return startConsole(consoleChat);
  }

  private AppConfig loadConfig() throws IOException {
    return new AppConfigLoader().load();
  }

  private HttpClient createHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private LlmClient createLlmClient(
      AppConfig config, HttpClient httpClient, ObjectMapper objectMapper) {

    HttpTransport transport = new JdkHttpTransport(httpClient);

    return new LoggingLlmClient(new OllamaClient(config, transport, objectMapper));
  }

  private PromptComposer createPromptComposer(PromptTemplateType templateType) {

    PromptTemplateLoader loader = new PromptTemplateLoader();

    PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    SystemPromptProvider provider = new SystemPromptProvider(templateType, loader, renderer);

    return new PromptComposer(provider);
  }

  private Agent createAgent(
      LlmClient llmClient, PromptComposer promptComposer, ObjectMapper objectMapper) {

    AgentLlmGateway llmGateway = new DefaultAgentLlmGateway(llmClient, promptComposer);

    return new ToolCallingAgent(
        llmGateway, new ToolDescriptionFormatter(), List.of(new CalculatorTool()), objectMapper);
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
