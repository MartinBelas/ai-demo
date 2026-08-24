package ai.demo.config;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Resolves environment configuration with an optional local .env fallback. */
public final class EnvironmentConfigLoader {

  private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final Function<String, String> systemEnvironment;
  private final Map<String, String> fileEnvironment;

  public EnvironmentConfigLoader(Path dotenvFile, Function<String, String> systemEnvironment) {
    this.systemEnvironment = systemEnvironment;
    this.fileEnvironment = load(dotenvFile);
  }

  /** Returns the system value first and falls back to the value loaded from .env. */
  public String get(String variable) {
    String systemValue = systemEnvironment.apply(variable);
    return systemValue == null || systemValue.isBlank()
        ? fileEnvironment.get(variable)
        : systemValue;
  }

  private Map<String, String> load(Path dotenvFile) {
    if (Files.notExists(dotenvFile)) {
      return Map.of();
    }

    try {
      Map<String, String> values = new HashMap<>();
      int lineNumber = 0;
      for (String line : Files.readAllLines(dotenvFile)) {
        lineNumber++;
        parseLine(line, lineNumber, values);
      }
      return Map.copyOf(values);
    } catch (IOException e) {
      throw new ConfigurationException("Unable to read environment file: " + dotenvFile, e);
    }
  }

  private void parseLine(String line, int lineNumber, Map<String, String> values) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      return;
    }

    int separator = trimmed.indexOf('=');
    if (separator < 1) {
      throw invalidLine(lineNumber);
    }

    String name = trimmed.substring(0, separator).trim();
    if (!VARIABLE_NAME.matcher(name).matches()) {
      throw invalidLine(lineNumber);
    }

    String value = trimmed.substring(separator + 1).trim();
    values.put(name, removeMatchingQuotes(value));
  }

  private String removeMatchingQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private ConfigurationException invalidLine(int lineNumber) {
    return new ConfigurationException("Invalid .env entry on line " + lineNumber);
  }
}
