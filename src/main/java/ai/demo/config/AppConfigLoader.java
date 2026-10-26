package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.function.UnaryOperator;

/** Loads application configuration from {@code application.properties}. */
public final class AppConfigLoader {

  private static final String CONFIG_FILE = "application.properties";
  private static final int DEFAULT_SERVER_PORT = 8080;
  private static final String MAX_RAG_CHUNKS_PROPERTY = "demo.limits.max-rag-chunks";
  private static final String MAX_RAG_CHUNKS_VARIABLE = "DEMO_LIMITS_MAX_RAG_CHUNKS";

  private final UnaryOperator<String> environment;

  public AppConfigLoader() {
    this(System::getenv);
  }

  /**
   * @param environment resolves environment overrides, e.g. the system environment with a local
   *     {@code .env} fallback ({@link EnvironmentConfigLoader#get})
   */
  public AppConfigLoader(UnaryOperator<String> environment) {
    this.environment = environment;
  }

  /**
   * Loads the application configuration.
   *
   * @return loaded application configuration
   * @throws IOException if the configuration file cannot be read
   */
  public AppConfig load() throws IOException {
    String externalConfig = System.getProperty("ai.demo.config");
    if (externalConfig == null || externalConfig.isBlank()) {
      return loadFromResource(CONFIG_FILE);
    }
    try (InputStream inputStream = Files.newInputStream(Path.of(externalConfig))) {
      return load(inputStream);
    }
  }

  AppConfig loadFromResource(String resourceName) throws IOException {

    Properties properties = loadProperties(resourceName);
    return createConfig(properties);
  }

  private AppConfig load(InputStream inputStream) throws IOException {
    Properties properties = new Properties();
    properties.load(inputStream);
    return createConfig(properties);
  }

  private AppConfig createConfig(Properties properties) {

    try {
      String configuredProvider = value(properties, "llm.provider", "LLM_PROVIDER", null);
      if (configuredProvider == null || configuredProvider.isBlank()) {
        throw new ConfigurationException("Required property 'llm.provider' is missing or empty");
      }
      LlmProvider provider = LlmProvider.from(configuredProvider);
      GenerationConfig generation =
          new GenerationConfig(
              requiredDouble(properties, "llm.temperature", "LLM_TEMPERATURE"),
              requiredInt(properties, "llm.max-output-tokens", "LLM_MAX_OUTPUT_TOKENS"),
              requiredProperty(properties, "llm.system-message"));
      OllamaConfig ollama = loadOllama(properties);
      OpenAiConfig openAi = loadOpenAi(properties);
      GroqConfig groq = loadGroq(properties);
      GeminiConfig gemini = loadGemini(properties);
      AppInterface appInterface = appInterface(properties);
      ServerConfig server = new ServerConfig(serverPort(properties));
      return new AppConfig(
          provider,
          generation,
          ollama,
          openAi,
          groq,
          gemini,
          Path.of(requiredProperty(properties, "conversation.file")),
          appInterface,
          server,
          loadDemoLimits(properties),
          loadRag(properties),
          loadAgentLab(properties));
    } catch (IllegalArgumentException e) {
      // Configuration records reject invalid values with IllegalArgumentException; the loader is
      // the single place that turns them into a ConfigurationException.
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  private RagConfig loadRag(Properties properties) {
    return new RagConfig(
        booleanValue(properties, "rag.enabled", "RAG_ENABLED"),
        EmbeddingProvider.from(
            value(properties, "rag.embedding.provider", "RAG_EMBEDDING_PROVIDER", "OLLAMA")),
        value(
            properties,
            "rag.embedding.base-url",
            "RAG_EMBEDDING_BASE_URL",
            "http://localhost:11434"),
        value(properties, "rag.embedding.model", "RAG_EMBEDDING_MODEL", "embeddinggemma"),
        value(properties, "rag.embedding.api-key-env", "RAG_EMBEDDING_API_KEY_ENV", null),
        intValue(properties, "rag.max-document-bytes", "RAG_MAX_DOCUMENT_BYTES", 200000),
        intValue(properties, "rag.max-documents", "RAG_MAX_DOCUMENTS", 20),
        intValue(properties, "rag.max-corpus-bytes", "RAG_MAX_CORPUS_BYTES", 400000),
        intValue(properties, "rag.max-chunk-bytes", "RAG_MAX_CHUNK_BYTES", 1200),
        intValue(properties, "rag.max-context-bytes", "RAG_MAX_CONTEXT_BYTES", 6000),
        intValue(properties, "rag.max-query-bytes", "RAG_MAX_QUERY_BYTES", 4000),
        intValue(properties, MAX_RAG_CHUNKS_PROPERTY, MAX_RAG_CHUNKS_VARIABLE, 5),
        parseDouble(
            value(properties, "rag.minimum-score", "RAG_MINIMUM_SCORE", "0.2"),
            "rag.minimum-score"));
  }

  private AgentLabConfig loadAgentLab(Properties properties) {
    return new AgentLabConfig(
        booleanValue(properties, "agent-lab.enabled", "AGENT_LAB_ENABLED"),
        value(properties, "agent-lab.base-url", "AGENT_LAB_BASE_URL", null),
        Duration.ofSeconds(
            intValue(properties, "agent-lab.timeout-seconds", "AGENT_LAB_TIMEOUT_SECONDS", 70)),
        AgentLabAuthentication.from(
            value(
                properties,
                "agent-lab.authentication",
                "AGENT_LAB_AUTHENTICATION",
                AgentLabAuthentication.NONE.name())));
  }

  private AppInterface appInterface(Properties properties) {
    String environmentInterface = environmentValue("APP_INTERFACE");
    if (environmentInterface != null) {
      return AppInterface.from(environmentInterface);
    }
    return AppInterface.from(properties.getProperty("app.interface"));
  }

  private int serverPort(Properties properties) {
    String environmentPort = environmentValue("PORT");
    if (environmentPort != null) {
      return parseInt(environmentPort, "PORT");
    }
    String configuredPort = properties.getProperty("server.port");
    if (configuredPort == null || configuredPort.isBlank()) {
      return DEFAULT_SERVER_PORT;
    }
    return parseInt(configuredPort, "server.port");
  }

  private GroqConfig loadGroq(Properties properties) {
    if (!properties.containsKey("groq.model")) return null;
    return new GroqConfig(
        requiredValue(properties, "groq.model", "GROQ_MODEL"),
        requiredValue(properties, "groq.base-url", "GROQ_BASE_URL"),
        requiredProperty(properties, "groq.api-key-env"));
  }

  private GeminiConfig loadGemini(Properties properties) {
    if (!properties.containsKey("gemini.model")) return null;
    return new GeminiConfig(
        requiredValue(properties, "gemini.model", "GEMINI_MODEL"),
        requiredValue(properties, "gemini.base-url", "GEMINI_BASE_URL"),
        requiredProperty(properties, "gemini.api-key-env"));
  }

  private OllamaConfig loadOllama(Properties properties) {
    if (!properties.containsKey("ollama.model")) return null;
    return new OllamaConfig(
        value(properties, "ollama.model", "OLLAMA_MODEL", null),
        value(properties, "ollama.base-url", "OLLAMA_BASE_URL", null),
        requiredInt(properties, "ollama.context-window", "OLLAMA_CONTEXT_WINDOW"),
        requiredDouble(properties, "ollama.repeat-penalty", "OLLAMA_REPEAT_PENALTY"),
        ollamaEnabled(properties));
  }

  private boolean ollamaEnabled(Properties properties) {
    String environmentValue = environmentValue("OLLAMA_ENABLED");
    if (environmentValue != null) {
      return parseBoolean(environmentValue, "OLLAMA_ENABLED");
    }
    String configuredValue = properties.getProperty("ollama.enabled");
    if (configuredValue == null || configuredValue.isBlank()) {
      return true;
    }
    return parseBoolean(configuredValue, "ollama.enabled");
  }

  private OpenAiConfig loadOpenAi(Properties properties) {
    if (!properties.containsKey("openai.model")) return null;
    return new OpenAiConfig(
        requiredValue(properties, "openai.model", "OPENAI_MODEL"),
        requiredValue(properties, "openai.base-url", "OPENAI_BASE_URL"),
        requiredProperty(properties, "openai.api-key-env"),
        booleanValue(
            properties, "openai.temperature-supported", "OPENAI_TEMPERATURE_SUPPORTED", true));
  }

  private DemoLimitsConfig loadDemoLimits(Properties properties) {
    return new DemoLimitsConfig(
        booleanValue(properties, "demo.limits.enabled", "DEMO_LIMITS_ENABLED"),
        booleanValue(properties, "demo.limits.firestore.enabled", "DEMO_LIMITS_FIRESTORE_ENABLED"),
        value(properties, "demo.limits.firestore.project-id", "GOOGLE_CLOUD_PROJECT", ""),
        value(
            properties, "demo.limits.firestore.database-id", "FIRESTORE_DATABASE_ID", "(default)"),
        properties.getProperty("demo.limits.ip-hash-salt-env", "DEMO_IP_HASH_SALT"),
        intValue(properties, "demo.limits.daily-requests", "DEMO_LIMITS_DAILY_REQUESTS", 1000),
        intValue(
            properties,
            "demo.limits.hourly-requests-per-ip",
            "DEMO_LIMITS_HOURLY_REQUESTS_PER_IP",
            20),
        intValue(properties, "demo.limits.concurrent-streams", "DEMO_LIMITS_CONCURRENT_STREAMS", 5),
        intValue(
            properties,
            "demo.limits.max-input-characters",
            "DEMO_LIMITS_MAX_INPUT_CHARACTERS",
            20000),
        intValue(
            properties, "demo.limits.max-history-messages", "DEMO_LIMITS_MAX_HISTORY_MESSAGES", 10),
        intValue(properties, MAX_RAG_CHUNKS_PROPERTY, MAX_RAG_CHUNKS_VARIABLE, 5),
        intValue(
            properties,
            "demo.limits.max-output-tokens-per-call",
            "DEMO_LIMITS_MAX_OUTPUT_TOKENS_PER_CALL",
            1500));
  }

  private boolean booleanValue(Properties properties, String property, String variable) {
    return booleanValue(properties, property, variable, false);
  }

  private boolean booleanValue(
      Properties properties, String property, String variable, boolean fallback) {
    String value = value(properties, property, variable, Boolean.toString(fallback));
    return parseBoolean(value, property);
  }

  private int intValue(Properties properties, String property, String variable, int fallback) {
    return parseInt(value(properties, property, variable, Integer.toString(fallback)), property);
  }

  private String value(Properties properties, String property, String variable, String fallback) {
    String environmentValue = environmentValue(variable);
    return environmentValue != null ? environmentValue : properties.getProperty(property, fallback);
  }

  /** Returns the environment override, or {@code null} when it is unset or blank. */
  private String environmentValue(String variable) {
    String environmentValue = environment.apply(variable);
    return environmentValue == null || environmentValue.isBlank() ? null : environmentValue;
  }

  private Properties loadProperties(String resourceName) throws IOException {

    Properties properties = new Properties();

    try (InputStream inputStream =
        AppConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {

      if (inputStream == null) {
        throw new ConfigurationException(resourceName + " not found");
      }

      properties.load(inputStream);
    }

    return properties;
  }

  private String requiredProperty(Properties properties, String key) {

    String value = properties.getProperty(key);

    if (value == null || value.isBlank()) {
      throw new ConfigurationException("Required property '" + key + "' is missing or empty");
    }

    return value;
  }

  private int requiredInt(Properties properties, String property, String variable) {
    return parseInt(requiredValue(properties, property, variable), property);
  }

  /** Returns the environment override if set, otherwise the required property value. */
  private String requiredValue(Properties properties, String property, String variable) {
    String environmentValue = environmentValue(variable);
    return environmentValue != null ? environmentValue : requiredProperty(properties, property);
  }

  private int parseInt(String value, String key) {

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw invalidProperty(key, "must be a valid integer", e);
    }
  }

  private double requiredDouble(Properties properties, String property, String variable) {
    return parseDouble(requiredValue(properties, property, variable), property);
  }

  private double parseDouble(String value, String key) {

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw invalidProperty(key, "must be a valid number", e);
    }
  }

  private boolean parseBoolean(String value, String key) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw invalidProperty(key, "must be true or false", null);
  }

  private static ConfigurationException invalidProperty(
      String key, String problem, Throwable cause) {
    return new ConfigurationException("Property '" + key + "' " + problem, cause);
  }
}
