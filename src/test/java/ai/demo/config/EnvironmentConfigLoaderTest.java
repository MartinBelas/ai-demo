package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentConfigLoaderTest {

  @TempDir Path directory;

  @Test
  void shouldPreferSystemEnvironmentOverDotenv() throws IOException {
    Path dotenv = directory.resolve(".env");
    Files.writeString(dotenv, "OPENAI_API_KEY=file-key\n");

    var environment =
        new EnvironmentConfigLoader(
            dotenv, variable -> variable.equals("OPENAI_API_KEY") ? "system-key" : null);

    assertEquals("system-key", environment.get("OPENAI_API_KEY"));
  }

  @Test
  void shouldLoadQuotedValueFromDotenvAndIgnoreComments() throws IOException {
    Path dotenv = directory.resolve(".env");
    Files.writeString(dotenv, "# Local secrets\nOPENAI_API_KEY=\"file-key\"\n");

    var environment = new EnvironmentConfigLoader(dotenv, variable -> null);

    assertEquals("file-key", environment.get("OPENAI_API_KEY"));
  }

  @Test
  void shouldAllowMissingDotenvFile() {
    var environment =
        new EnvironmentConfigLoader(directory.resolve("missing.env"), variable -> null);

    assertNull(environment.get("OPENAI_API_KEY"));
  }

  @Test
  void shouldRejectInvalidDotenvEntry() throws IOException {
    Path dotenv = directory.resolve(".env");
    Files.writeString(dotenv, "invalid entry\n");

    assertThrows(
        ConfigurationException.class, () -> new EnvironmentConfigLoader(dotenv, variable -> null));
  }
}
