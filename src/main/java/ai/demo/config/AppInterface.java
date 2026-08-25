package ai.demo.config;

import java.util.Locale;

/** User interface started by the application. */
public enum AppInterface {
  CONSOLE,
  SERVER;

  public static AppInterface from(String value) {
    if (value == null || value.isBlank()) {
      return CONSOLE;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown app.interface: " + value, e);
    }
  }
}
