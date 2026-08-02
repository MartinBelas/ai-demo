package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
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
    return loadFromResource(CONFIG_FILE);
  }

  AppConfig loadFromResource(String resourceName) throws IOException {

    Properties properties = loadProperties(resourceName);

    return new AppConfig(
        requiredProperty(properties, "llm.base-url"),
        requiredProperty(properties, "llm.model"),
        requiredDouble(properties, "llm.temperature"),
        requiredInt(properties, "llm.num-predict"),
        requiredInt(properties, "llm.num-ctx"));
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
