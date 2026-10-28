package ai.demo;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentLlmGateway;
import ai.demo.agent.DefaultAgentLlmGateway;
import ai.demo.agent.RemoteAgent;
import ai.demo.agent.ToolCallingAgent;
import ai.demo.agent.tool.CalculatorTool;
import ai.demo.agent.tool.ToolDescriptionFormatter;
import ai.demo.api.ApiServer;
import ai.demo.api.ChatServiceResolver;
import ai.demo.api.ChatServiceSelector;
import ai.demo.api.DemoProtection;
import ai.demo.client.EmbeddingClientFactory;
import ai.demo.client.LlmClient;
import ai.demo.client.LlmClientFactory;
import ai.demo.client.LoggingLlmClient;
import ai.demo.client.SwitchableLlmClient;
import ai.demo.client.agentlab.AgentLabClient;
import ai.demo.client.http.HttpTransport;
import ai.demo.client.http.JdkHttpTransport;
import ai.demo.config.AppConfig;
import ai.demo.config.AppConfigLoader;
import ai.demo.config.AppInterface;
import ai.demo.config.EnvironmentConfigLoader;
import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import ai.demo.console.ConsoleChat;
import ai.demo.console.command.CommandRegistry;
import ai.demo.console.command.ConsoleCommandDispatcher;
import ai.demo.exception.ConfigurationException;
import ai.demo.exception.LlmCommunicationException;
import ai.demo.exception.RagException;
import ai.demo.exception.ServerException;
import ai.demo.persistence.BundledRagDocuments;
import ai.demo.persistence.ConversationRepository;
import ai.demo.persistence.FileConversationRepository;
import ai.demo.persistence.InMemoryVectorStore;
import ai.demo.prompt.PromptComposer;
import ai.demo.prompt.RagPromptComposer;
import ai.demo.prompt.template.PromptTemplateLoader;
import ai.demo.prompt.template.PromptTemplateRenderer;
import ai.demo.prompt.template.PromptTemplateType;
import ai.demo.prompt.template.SystemPromptProvider;
import ai.demo.service.ChatService;
import ai.demo.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Composition root: wiring every provider/service/config collaborator here is the point.
@SuppressWarnings("java:S6539")
public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);
  private static final String CONFIGURATION_ERROR_LOG = "Configuration error: {}";

  public static void main(String[] args) {
    int exitCode = new App().run();

    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  public int run() {

    // Configuration overrides and API keys share one source: the system environment with a local
    // .env fallback, read once for the whole application.
    UnaryOperator<String> environment;
    AppConfig config;

    try {
      environment = new EnvironmentConfigLoader(Path.of(".env"), System::getenv)::get;
      config = new AppConfigLoader(environment).load();
    } catch (ConfigurationException | IOException e) {
      log.error(CONFIGURATION_ERROR_LOG, e.getMessage(), e);
      return 1;
    }

    if (config.appInterface() == AppInterface.HTTP) {
      return startServer(config, environment);
    }

    HttpClient httpClient = createHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    SwitchableLlmClient providerClient;
    try {
      providerClient = createLlmClient(config, httpClient, objectMapper, environment);
    } catch (ConfigurationException e) {
      httpClient.shutdownNow();
      log.error(CONFIGURATION_ERROR_LOG, e.getMessage(), e);
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

  // Package-private for tests (AppRagWiringTest); not part of the application's API.
  int startServer(AppConfig config, UnaryOperator<String> environment) {
    try (HttpClient httpClient = createHttpClient()) {
      LlmProviderAvailability providerAvailability =
          new LlmProviderAvailability(config, environment);
      ObjectMapper objectMapper = new ObjectMapper();
      HttpTransport transport = new JdkHttpTransport(httpClient);
      LlmClientFactory clientFactory = new LlmClientFactory(transport, objectMapper, environment);
      ChatServiceResolver chatServiceResolver =
          createChatServiceResolver(
              config,
              clientFactory,
              objectMapper,
              createRagService(config, transport, objectMapper, environment));
      ChatService agentChatService = createAgentChatService(config, transport, objectMapper);
      String ipHashSalt = environment.apply(config.demoLimits().ipHashSaltEnvironmentVariable());
      if (config.demoLimits().enabled() && (ipHashSalt == null || ipHashSalt.isBlank())) {
        throw new ConfigurationException(
            "Demo IP hash salt is required when public limits are enabled");
      }
      DemoProtection demoProtection = DemoProtection.configured(config.demoLimits(), ipHashSalt);
      return runServer(
          config,
          providerAvailability,
          chatServiceResolver,
          agentChatService,
          objectMapper,
          demoProtection);
    } catch (ConfigurationException e) {
      log.error(CONFIGURATION_ERROR_LOG, e.getMessage(), e);
      return 1;
    }
  }

  private int runServer(
      AppConfig config,
      LlmProviderAvailability providerAvailability,
      ChatServiceResolver chatServiceResolver,
      ChatService agentChatService,
      ObjectMapper objectMapper,
      DemoProtection demoProtection) {
    try (ApiServer server =
        new ApiServer(
            config.server().port(),
            providerAvailability,
            new ChatServiceSelector(chatServiceResolver, agentChatService),
            config.provider(),
            objectMapper,
            demoProtection,
            config.rag().enabled())) {
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

  // Package-private for tests (AppRagWiringTest); not part of the application's API.
  RagService createRagService(
      AppConfig config,
      HttpTransport transport,
      ObjectMapper objectMapper,
      UnaryOperator<String> environment) {
    if (!config.rag().enabled()) {
      return null;
    }
    EmbeddingClientFactory embeddingClientFactory =
        new EmbeddingClientFactory(transport, objectMapper, environment);
    RagService ragService =
        new RagService(
            config.rag(),
            embeddingClientFactory.create(config.rag()),
            new InMemoryVectorStore(),
            new BundledRagDocuments(config.rag())::load);
    try {
      ragService.verifyDocuments();
    } catch (RagException e) {
      throw new ConfigurationException("Invalid RAG documents: " + e.getMessage(), e);
    }
    return ragService;
  }

  /** Agent mode is served by the external agent service; {@code null} when it is disabled. */
  private ChatService createAgentChatService(
      AppConfig config, HttpTransport transport, ObjectMapper objectMapper) {
    if (!config.agentLab().enabled()) {
      return null;
    }
    log.info("Agent mode enabled (agent service: {})", config.agentLab().baseUrl());
    return new ChatService(
        new RemoteAgent(new AgentLabClient(config.agentLab(), transport, objectMapper)));
  }

  private ChatServiceResolver createChatServiceResolver(
      AppConfig config, LlmClientFactory clientFactory, ObjectMapper objectMapper, RagService rag) {
    ConcurrentMap<LlmProvider, ChatService> services = new ConcurrentHashMap<>();
    PromptComposer promptComposer = createAgentPromptComposer(config);
    RagPromptComposer ragPromptComposer = new RagPromptComposer(objectMapper);
    return provider ->
        services.computeIfAbsent(
            provider,
            selectedProvider -> {
              LlmClient client =
                  new LoggingLlmClient(clientFactory.create(config, selectedProvider));
              return new ChatService(
                  createAgent(client, promptComposer, objectMapper), rag, ragPromptComposer);
            });
  }

  // Package-private so tests can substitute the client; not part of the application's API.
  HttpClient createHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private SwitchableLlmClient createLlmClient(
      AppConfig config,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      UnaryOperator<String> environment) {

    HttpTransport transport = new JdkHttpTransport(httpClient);

    return new LlmClientFactory(transport, objectMapper, environment).createSwitchable(config);
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
