package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.UnaryOperator;

/** Loads application configuration from {@code application.properties}. */
public final class AppConfigLoader {

  private static final String CONFIG_FILE = "application.properties";
  private static final int DEFAULT_SERVER_PORT = 8080;

  private final UnaryOperator<String> environment;

  public AppConfigLoader() {
    this(System::getenv);
  }

  AppConfigLoader(UnaryOperator<String> environment) {
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
      LlmProvider provider = LlmProvider.from(requiredProperty(properties, "llm.provider"));
      GenerationConfig generation =
          new GenerationConfig(
              requiredDouble(properties, "llm.temperature"),
              requiredInt(properties, "llm.max-output-tokens"),
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
          server);
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  private AppInterface appInterface(Properties properties) {
    String environmentInterface = environment.apply("APP_INTERFACE");
    if (environmentInterface != null && !environmentInterface.isBlank()) {
      return AppInterface.from(environmentInterface);
    }
    return AppInterface.from(properties.getProperty("app.interface"));
  }

  private int serverPort(Properties properties) {
    String environmentPort = environment.apply("PORT");
    if (environmentPort != null && !environmentPort.isBlank()) {
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
        requiredProperty(properties, "groq.model"),
        requiredProperty(properties, "groq.base-url"),
        requiredProperty(properties, "groq.api-key-env"));
  }

  private GeminiConfig loadGemini(Properties properties) {
    if (!properties.containsKey("gemini.model")) return null;
    return new GeminiConfig(
        requiredProperty(properties, "gemini.model"),
        requiredProperty(properties, "gemini.base-url"),
        requiredProperty(properties, "gemini.api-key-env"));
  }

  private OllamaConfig loadOllama(Properties properties) {
    if (!properties.containsKey("ollama.model")) return null;
    return new OllamaConfig(
        requiredProperty(properties, "ollama.model"),
        requiredProperty(properties, "ollama.base-url"),
        requiredInt(properties, "ollama.context-window"),
        requiredDouble(properties, "ollama.repeat-penalty"),
        ollamaEnabled(properties));
  }

  private boolean ollamaEnabled(Properties properties) {
    String environmentValue = environment.apply("OLLAMA_ENABLED");
    if (environmentValue != null && !environmentValue.isBlank()) {
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
        requiredProperty(properties, "openai.model"),
        requiredProperty(properties, "openai.base-url"),
        requiredProperty(properties, "openai.api-key-env"));
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

  private int requiredInt(Properties properties, String key) {

    String value = requiredProperty(properties, key);

    return parseInt(value, key);
  }

  private int parseInt(String value, String key) {

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ConfigurationException("Property '" + key + "' must be a valid integer", e);
    }
  }

  private double requiredDouble(Properties properties, String key) {

    String value = requiredProperty(properties, key);

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new ConfigurationException("Property '" + key + "' must be a valid number", e);
    }
  }

  private boolean parseBoolean(String value, String key) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new ConfigurationException("Property '" + key + "' must be true or false");
  }
}
