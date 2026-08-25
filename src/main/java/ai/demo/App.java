package ai.demo;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentLlmGateway;
import ai.demo.agent.DefaultAgentLlmGateway;
import ai.demo.agent.ToolCallingAgent;
import ai.demo.agent.tool.CalculatorTool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.api.ApiServer;
import ai.demo.client.LlmClient;
import ai.demo.client.LlmClientFactory;
import ai.demo.client.LoggingLlmClient;
import ai.demo.client.SwitchableLlmClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.http.JdkHttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.AppConfigLoader;
import ai.demo.config.AppInterface;
import ai.demo.config.EnvironmentConfigLoader;
import ai.demo.console.ConsoleChat;
import ai.demo.console.command.CommandRegistry;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.exception.ServerException;
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
import java.nio.file.Path;
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
    } catch (ConfigurationException | IOException e) {
      log.error("Configuration error: {}", e.getMessage(), e);
      return 1;
    }

    if (config.appInterface() == AppInterface.HTTP) {
      return startServer(config);
    }

    HttpClient httpClient = createHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    SwitchableLlmClient providerClient;
    try {
      providerClient = createLlmClient(config, httpClient, objectMapper);
    } catch (ConfigurationException e) {
      httpClient.shutdownNow();
      log.error("Configuration error: {}", e.getMessage(), e);
      return 1;
    }
    LlmClient llmClient = new LoggingLlmClient(providerClient);

    PromptComposer agentPromptComposer = createAgentPromptComposer(config);

    Agent agent = createAgent(llmClient, agentPromptComposer, objectMapper);

    ChatService chatService = new ChatService(agent);

    ConsoleCommandDispatcher dispatcher =
        new ConsoleCommandDispatcher(new CommandRegistry(providerClient).commands());

    ConversationRepository conversationRepository =
        new FileConversationRepository(config.conversationFile(), objectMapper);

    ConsoleChat consoleChat =
        new ConsoleChat(chatService, config, dispatcher, conversationRepository);

    addShutdownHook(httpClient);

    return startConsole(consoleChat);
  }

  private int startServer(AppConfig config) {
    try (ApiServer server = new ApiServer(config.server().port())) {
      server.start();
      Runtime.getRuntime().addShutdownHook(new Thread(server::close));
      log.info("HTTP server started on port {}", server.port());
      server.awaitShutdown();
      return 0;
    } catch (ServerException e) {
      log.error("HTTP server error: {}", e.getMessage(), e);
      return 3;
    }
  }

  private AppConfig loadConfig() throws IOException {
    return new AppConfigLoader().load();
  }

  private HttpClient createHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private SwitchableLlmClient createLlmClient(
      AppConfig config, HttpClient httpClient, ObjectMapper objectMapper) {

    HttpTransport transport = new JdkHttpTransport(httpClient);

    EnvironmentConfigLoader environment =
        new EnvironmentConfigLoader(Path.of(".env"), System::getenv);
    return new LlmClientFactory(transport, objectMapper, environment::get).createSwitchable(config);
  }

  private PromptComposer createAgentPromptComposer(AppConfig config) {

    PromptTemplateLoader loader = new PromptTemplateLoader();

    PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    SystemPromptProvider provider =
        new SystemPromptProvider(PromptTemplateType.AGENT, loader, renderer);

    return new PromptComposer(
        provider, java.util.Map.of("systemMessage", config.generation().systemMessage()));
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

    } catch (RuntimeException e) {
      log.error("Unexpected error: {}", e.getMessage(), e);
      return 99;
    }
  }
}
