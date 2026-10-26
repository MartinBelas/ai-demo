package ai.demo.config;

import java.nio.file.Path;

/**
 * Builds {@link AppConfig} instances for tests. Holds the defaults for sections a test does not
 * care about in one place, so adding a configuration section does not require changing every test
 * or adding another {@code AppConfig} constructor.
 */
public final class TestAppConfig {

  private final LlmProvider provider;
  private GenerationConfig generation = new GenerationConfig(0.4, 300, "Be helpful.");
  private OllamaConfig ollama;
  private OpenAiConfig openAi;
  private GroqConfig groq;
  private GeminiConfig gemini;
  private Path conversationFile = Path.of("conversation.json");
  private final AppInterface appInterface = AppInterface.CONSOLE;
  private final ServerConfig server = new ServerConfig(8080);
  private final DemoLimitsConfig demoLimits = DemoLimitsConfig.disabled();
  private RagConfig rag = RagConfig.disabled();
  private final AgentLabConfig agentLab = AgentLabConfig.disabled();

  private TestAppConfig(LlmProvider provider) {
    this.provider = provider;
  }

  /** An enabled RAG section with default limits except the per-document byte limit. */
  public static RagConfig enabledRag(EmbeddingProvider provider, int maxDocumentBytes) {
    return new RagConfig(
        true,
        provider,
        "http://localhost:11434",
        "embeddinggemma",
        provider == EmbeddingProvider.OLLAMA ? null : "TEST_EMBEDDING_API_KEY",
        maxDocumentBytes,
        20,
        400000,
        1200,
        6000,
        4000,
        5,
        0.2);
  }

  public static TestAppConfig builder(LlmProvider provider) {
    return new TestAppConfig(provider);
  }

  public TestAppConfig generation(GenerationConfig generation) {
    this.generation = generation;
    return this;
  }

  public TestAppConfig ollama(OllamaConfig ollama) {
    this.ollama = ollama;
    return this;
  }

  public TestAppConfig openAi(OpenAiConfig openAi) {
    this.openAi = openAi;
    return this;
  }

  public TestAppConfig groq(GroqConfig groq) {
    this.groq = groq;
    return this;
  }

  public TestAppConfig gemini(GeminiConfig gemini) {
    this.gemini = gemini;
    return this;
  }

  public TestAppConfig rag(RagConfig rag) {
    this.rag = rag;
    return this;
  }

  public TestAppConfig conversationFile(Path conversationFile) {
    this.conversationFile = conversationFile;
    return this;
  }

  public AppConfig build() {
    return new AppConfig(
        provider,
        generation,
        ollama,
        openAi,
        groq,
        gemini,
        conversationFile,
        appInterface,
        server,
        demoLimits,
        rag,
        agentLab);
  }
}
