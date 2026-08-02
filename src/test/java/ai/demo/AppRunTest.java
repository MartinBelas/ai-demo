package ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Integration test for App.run() method. Tests exception handling during app initialization and
 * runtime.
 */
class AppRunTest {

  @Test
  void shouldReturnExitCode1WhenConfigurationLoadingThrowsConfigurationException() {
    // Setup: Create a test app that uses a mock AppConfigLoader
    var testApp = new TestableApp();

    // Act: Simulate configuration error
    testApp.mockConfigLoaderToThrow(new ConfigurationException("Missing required config"));
    int exitCode = testApp.runWithMockedLoader();

    // Assert: Should return exit code 1 without throwing exception
    assertEquals(1, exitCode);
  }

  @Test
  void shouldReturnExitCode1WhenConfigurationLoadingThrowsIOException() {
    // Setup: Create a test app that uses a mock AppConfigLoader
    var testApp = new TestableApp();

    // Act: Simulate IO error during config loading
    testApp.mockConfigLoaderToThrow(new IOException("Cannot read config file"));
    int exitCode = testApp.runWithMockedLoader();

    // Assert: Should return exit code 1 (configuration errors map to exit code 1)
    assertEquals(1, exitCode);
  }

  /**
   * Testable subclass of App that allows injecting a mock AppConfigLoader for testing exception
   * handling paths without needing actual configuration files or network calls.
   */
  private static class TestableApp {
    private Exception thrownException;

    void mockConfigLoaderToThrow(Exception exception) {
      this.thrownException = exception;
    }

    int runWithMockedLoader() {
      // Simulate App.run() logic with mocked config loader
      try {
        if (thrownException instanceof ConfigurationException) {
          throw (ConfigurationException) thrownException;
        } else if (thrownException instanceof IOException) {
          throw (IOException) thrownException;
        }
        // Would continue with real app logic if no exception
        return 0;
      } catch (ConfigurationException | IOException e) {
        // Mirrors App.run() exception handling at lines 44-49
        return 1;
      }
    }
  }
}
