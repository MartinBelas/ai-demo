package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads application configuration from {@code application.properties}. */
public final class AppConfigLoader {

  private static final String CONFIG_FILE = "application.properties";

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
      return new AppConfig(
          provider,
          generation,
          ollama,
          openAi,
          groq,
          gemini,
          Path.of(requiredProperty(properties, "conversation.file")));
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
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
        requiredDouble(properties, "ollama.repeat-penalty"));
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
}
