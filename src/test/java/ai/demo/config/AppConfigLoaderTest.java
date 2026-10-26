package ai.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.exception.ConfigurationException;
import ai.demo.persistence.BundledRagDocuments;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppConfigLoaderTest {

  // No environment at all: the real one (e.g. AGENT_LAB_ENABLED set in the developer's shell)
  // must not change what these tests load.
  private final AppConfigLoader loader = new AppConfigLoader(variable -> null);

  @Test
  void shouldLoadConfiguration() throws IOException {

    AppConfig config = loader.loadFromResource("app-config/valid.properties");

    assertEquals(LlmProvider.OLLAMA, config.provider());
    assertEquals("http://localhost:11434", config.ollama().baseUrl());
    assertEquals("qwen3:4b", config.model());
    assertEquals(0.7, config.generation().temperature());
    assertEquals(2000, config.generation().maxOutputTokens());
    assertEquals(4096, config.ollama().contextWindow());
    assertEquals(1.2, config.ollama().repeatPenalty());
    assertTrue(config.ollama().enabled());
    assertEquals(AppInterface.CONSOLE, config.appInterface());
    assertEquals(8080, config.server().port());
  }

  @Test
  void shouldLoadServerConfiguration() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-http.properties");

    assertEquals(AppInterface.HTTP, config.appInterface());
    assertEquals(7070, config.server().port());
  }

  @Test
  void shouldOverrideServerConfigurationFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader = new AppConfigLoader(this::serverEnvironment);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertEquals(AppInterface.HTTP, config.appInterface());
    assertEquals(9090, config.server().port());
  }

  @Test
  void shouldLoadPublicDemoLimitsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "DEMO_LIMITS_ENABLED" -> "true";
                  case "DEMO_LIMITS_DAILY_REQUESTS" -> "25";
                  case "DEMO_LIMITS_MAX_OUTPUT_TOKENS_PER_CALL" -> "2000";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertTrue(config.demoLimits().enabled());
    assertEquals(25, config.demoLimits().dailyRequests());
  }

  private String serverEnvironment(String key) {
    return switch (key) {
      case "APP_INTERFACE" -> "http";
      case "PORT" -> "9090";
      default -> null;
    };
  }

  @Test
  void shouldLoadDemoLimitProperties() throws IOException {
    DemoLimitsConfig limits =
        loader.loadFromResource("app-config/valid-demo-limits.properties").demoLimits();
    assertTrue(limits.enabled());
    assertEquals(37, limits.dailyRequests());
    assertEquals(9, limits.hourlyRequestsPerIp());
    assertEquals("DEMO_IP_HASH_SALT", limits.ipHashSaltEnvironmentVariable());
  }

  @Test
  void shouldOverrideDemoLimitsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "DEMO_LIMITS_ENABLED" -> "true";
                  case "DEMO_LIMITS_FIRESTORE_ENABLED" -> "true";
                  case "DEMO_LIMITS_DAILY_REQUESTS" -> "73";
                  case "DEMO_LIMITS_HOURLY_REQUESTS_PER_IP" -> "11";
                  case "DEMO_LIMITS_CONCURRENT_STREAMS" -> "2";
                  default -> null;
                });
    DemoLimitsConfig limits =
        environmentLoader.loadFromResource("app-config/valid-demo-limits.properties").demoLimits();
    assertTrue(limits.enabled());
    assertTrue(limits.firestoreEnabled());
    assertEquals(73, limits.dailyRequests());
    assertEquals(11, limits.hourlyRequestsPerIp());
    assertEquals(2, limits.concurrentStreams());
  }

  @Test
  void shouldOverrideOllamaAvailabilityFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OLLAMA_ENABLED".equals(key) ? "false" : null);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid-openai.properties");

    assertFalse(config.ollama().enabled());
  }

  @Test
  void shouldOverrideOllamaConnectionFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "OLLAMA_MODEL" -> "qwen3:8b";
                  case "OLLAMA_BASE_URL" -> "http://ollama:11434";
                  case "OLLAMA_CONTEXT_WINDOW" -> "8192";
                  case "OLLAMA_REPEAT_PENALTY" -> "1.05";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertEquals("qwen3:8b", config.ollama().model());
    assertEquals("http://ollama:11434", config.ollama().baseUrl());
    assertEquals(8192, config.ollama().contextWindow());
    assertEquals(1.05, config.ollama().repeatPenalty());
  }

  @Test
  void shouldOverrideGenerationSettingsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "LLM_TEMPERATURE" -> "0.1";
                  case "LLM_MAX_OUTPUT_TOKENS" -> "512";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertEquals(0.1, config.generation().temperature());
    assertEquals(512, config.generation().maxOutputTokens());
  }

  @Test
  void shouldRejectInvalidTemperatureFromEnvironment() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "LLM_TEMPERATURE".equals(key) ? "warm" : null);

    assertThrows(
        ConfigurationException.class,
        () -> environmentLoader.loadFromResource("app-config/valid.properties"));
  }

  @Test
  void shouldOverrideCloudProviderConnectionsFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "OPENAI_MODEL" -> "gpt-override";
                  case "OPENAI_BASE_URL" -> "http://openai-proxy/v1";
                  case "GROQ_MODEL" -> "groq-override";
                  case "GROQ_BASE_URL" -> "http://groq-proxy/v1";
                  case "GEMINI_MODEL" -> "gemini-override";
                  case "GEMINI_BASE_URL" -> "http://gemini-proxy/v1beta";
                  default -> null;
                });

    AppConfig config =
        environmentLoader.loadFromResource("app-config/valid-all-providers.properties");

    assertEquals("gpt-override", config.openAi().model());
    assertEquals("http://openai-proxy/v1", config.openAi().baseUrl());
    assertEquals("groq-override", config.groq().model());
    assertEquals("http://groq-proxy/v1", config.groq().baseUrl());
    assertEquals("gemini-override", config.gemini().model());
    assertEquals("http://gemini-proxy/v1beta", config.gemini().baseUrl());
  }

  @Test
  void shouldKeepProviderPropertiesWithoutEnvironmentOverrides() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-all-providers.properties");

    assertEquals("openai-test-model", config.openAi().model());
    assertEquals("groq-test-model", config.groq().model());
    assertEquals("gemini-test-model", config.gemini().model());
  }

  @Test
  void shouldRejectOllamaEmbeddingWhenOllamaIsDisabled() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "OLLAMA_ENABLED" -> "false";
                  case "RAG_ENABLED" -> "true";
                  default -> null;
                });

    ConfigurationException exception =
        assertThrows(
            ConfigurationException.class,
            () -> environmentLoader.loadFromResource("app-config/valid-openai.properties"));
    assertTrue(exception.getMessage().contains("RAG embedding provider OLLAMA"));
  }

  @Test
  void shouldAllowCloudEmbeddingWhenOllamaIsDisabled() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "OLLAMA_ENABLED" -> "false";
                  case "RAG_ENABLED" -> "true";
                  case "RAG_EMBEDDING_PROVIDER" -> "OPENAI";
                  case "RAG_EMBEDDING_BASE_URL" -> "https://api.openai.com/v1";
                  case "RAG_EMBEDDING_MODEL" -> "text-embedding-3-small";
                  case "RAG_EMBEDDING_API_KEY_ENV" -> "OPENAI_API_KEY";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid-openai.properties");

    assertTrue(config.rag().enabled());
    assertEquals(EmbeddingProvider.OPENAI, config.rag().provider());
  }

  @Test
  void shouldAllowOllamaEmbeddingWhenRagIsDisabled() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OLLAMA_ENABLED".equals(key) ? "false" : null);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid-openai.properties");

    assertFalse(config.rag().enabled());
  }

  @Test
  void shouldNotConfigureProviderFromEnvironmentModelAlone() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "GROQ_MODEL".equals(key) ? "groq-override" : null);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertNull(config.groq());
  }

  @Test
  void shouldRejectDisabledSelectedOllamaProvider() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OLLAMA_ENABLED".equals(key) ? "false" : null);

    assertThrows(
        ConfigurationException.class,
        () -> environmentLoader.loadFromResource("app-config/valid.properties"));
  }

  @Test
  void shouldLoadOpenAiConfiguration() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-openai.properties");

    assertEquals(LlmProvider.OPENAI, config.provider());
    assertEquals("https://api.openai.com/v1", config.openAi().baseUrl());
    assertEquals("OPENAI_API_KEY", config.openAi().apiKeyEnvironmentVariable());
    assertTrue(config.openAi().temperatureSupported());
  }

  @Test
  void shouldDisableOpenAiTemperatureFromProperty() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid-openai-no-temperature.properties");

    assertFalse(config.openAi().temperatureSupported());
  }

  @Test
  void shouldOverrideOpenAiTemperatureSupportFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "OPENAI_TEMPERATURE_SUPPORTED".equals(key) ? "false" : null);

    AppConfig config = environmentLoader.loadFromResource("app-config/valid-openai.properties");

    assertFalse(config.openAi().temperatureSupported());
  }

  @Test
  void shouldRejectUnknownProvider() {
    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/unknown-provider.properties"));
  }

  @Test
  void shouldFailWhenResourceDoesNotExist() {

    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/does-not-exist.properties"));
  }

  @Test
  void shouldDisableAgentLabByDefault() throws IOException {
    AppConfig config = loader.loadFromResource("app-config/valid.properties");

    assertFalse(config.agentLab().enabled());
  }

  @Test
  void shouldLoadAgentLabFromEnvironment() throws IOException {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(
            key ->
                switch (key) {
                  case "AGENT_LAB_ENABLED" -> "true";
                  case "AGENT_LAB_BASE_URL" -> "http://localhost:8081/";
                  case "AGENT_LAB_TIMEOUT_SECONDS" -> "90";
                  case "AGENT_LAB_AUTHENTICATION" -> "google_id_token";
                  default -> null;
                });

    AppConfig config = environmentLoader.loadFromResource("app-config/valid.properties");

    assertTrue(config.agentLab().enabled());
    assertEquals("http://localhost:8081", config.agentLab().baseUrl());
    assertEquals(java.time.Duration.ofSeconds(90), config.agentLab().timeout());
    assertEquals(AgentLabAuthentication.GOOGLE_ID_TOKEN, config.agentLab().authentication());
  }

  @Test
  void shouldFailWhenAgentLabIsEnabledWithoutBaseUrl() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "AGENT_LAB_ENABLED".equals(key) ? "true" : null);

    assertThrows(
        ConfigurationException.class,
        () -> environmentLoader.loadFromResource("app-config/valid.properties"));
  }

  @Test
  void shouldFailWhenRequiredPropertyIsMissing() {

    assertThrows(
        ConfigurationException.class,
        () -> loader.loadFromResource("app-config/missing-model.properties"));
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource({
    "app-config/invalid-temperature.properties, temperature",
    "app-config/invalid-num-predict.properties, maxOutputTokens",
    "app-config/invalid-num-ctx.properties, contextWindow",
    "app-config/invalid-repeat-penalty.properties, repeatPenalty"
  })
  void shouldFailForInvalidNumericProperty(String resource, String propertyName) {

    assertThrows(ConfigurationException.class, () -> loader.loadFromResource(resource));
  }

  @Test
  void shouldNameTheRagMinimumScorePropertyWhenEnvironmentValueIsInvalid() {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> "RAG_MINIMUM_SCORE".equals(key) ? "high" : null);

    ConfigurationException exception =
        assertThrows(
            ConfigurationException.class,
            () -> environmentLoader.loadFromResource("app-config/valid.properties"));
    assertTrue(exception.getMessage().contains("rag.minimum-score"));
  }

  @ParameterizedTest
  @CsvSource({"LLM_TEMPERATURE,NaN", "LLM_TEMPERATURE,Infinity", "OLLAMA_REPEAT_PENALTY,NaN"})
  void shouldRejectNonFiniteNumbersFromEnvironment(String variable, String value) {
    AppConfigLoader environmentLoader =
        new AppConfigLoader(key -> variable.equals(key) ? value : null);

    assertThrows(
        ConfigurationException.class,
        () -> environmentLoader.loadFromResource("app-config/valid.properties"));
  }

  @Test
  void shouldShipBundledRagDocumentsThatFitTheShippedLimits() throws IOException {
    AppConfig config = loader.loadFromResource("application.properties");

    assertFalse(new BundledRagDocuments(config.rag()).load().isEmpty());
  }
}
