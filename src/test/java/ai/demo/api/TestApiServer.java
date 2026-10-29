package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.config.LlmProviderAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds {@link ApiServer} instances for tests with defaults for everything a test does not set, so
 * the server needs only its one production constructor.
 */
final class TestApiServer {

  /** Lets the operating system pick a free port, so tests can run in parallel. */
  private static final int EPHEMERAL_PORT = 0;

  private LlmProviderAvailability providerAvailability;
  private ChatServiceSelector chatServices;
  private ObjectMapper objectMapper = new ObjectMapper();
  private DemoProtection demoProtection = DemoProtection.localDevelopment();

  private TestApiServer() {}

  static TestApiServer create() {
    return new TestApiServer();
  }

  TestApiServer providerAvailability(LlmProviderAvailability providerAvailability) {
    this.providerAvailability = providerAvailability;
    return this;
  }

  /** Provider chat services without agent mode. */
  TestApiServer chatServices(ChatServiceResolver chatServiceResolver) {
    return chatServices(new ChatServiceSelector(chatServiceResolver, null));
  }

  /** Provider chat services together with the agent-mode service. */
  TestApiServer chatServices(ChatServiceSelector chatServices) {
    this.chatServices = chatServices;
    return this;
  }

  TestApiServer objectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  TestApiServer demoProtection(DemoProtection demoProtection) {
    this.demoProtection = demoProtection;
    return this;
  }

  ApiServer build() {
    return new ApiServer(
        EPHEMERAL_PORT,
        providerAvailability,
        chatServices,
        LlmProvider.OLLAMA,
        objectMapper,
        demoProtection,
        false);
  }
}
