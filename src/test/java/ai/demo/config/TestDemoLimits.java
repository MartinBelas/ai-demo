package ai.demo.config;

/**
 * Builds enabled {@link DemoLimitsConfig} instances for tests by name instead of twelve positional
 * constructor arguments. Defaults match the production defaults with in-memory quota storage.
 */
public final class TestDemoLimits {

  private static final int MAX_HISTORY_MESSAGES = 10;
  private static final int MAX_RAG_CHUNKS = 5;

  private boolean firestoreEnabled;
  private String firestoreProjectId = "";
  private String firestoreDatabaseId = "(default)";
  private String ipHashSaltEnvironmentVariable = "DEMO_IP_HASH_SALT";
  private int dailyRequests = 1000;
  private int hourlyRequestsPerIp = 20;
  private int concurrentStreams = 5;
  private int maxInputCharacters = 20000;
  private int maxOutputTokensPerCall = 1500;

  private TestDemoLimits() {}

  public static TestDemoLimits enabled() {
    return new TestDemoLimits();
  }

  public TestDemoLimits firestore(String projectId, String databaseId) {
    this.firestoreEnabled = true;
    this.firestoreProjectId = projectId;
    this.firestoreDatabaseId = databaseId;
    return this;
  }

  public TestDemoLimits ipHashSaltEnvironmentVariable(String variable) {
    this.ipHashSaltEnvironmentVariable = variable;
    return this;
  }

  public TestDemoLimits dailyRequests(int dailyRequests) {
    this.dailyRequests = dailyRequests;
    return this;
  }

  public TestDemoLimits hourlyRequestsPerIp(int hourlyRequestsPerIp) {
    this.hourlyRequestsPerIp = hourlyRequestsPerIp;
    return this;
  }

  public TestDemoLimits concurrentStreams(int concurrentStreams) {
    this.concurrentStreams = concurrentStreams;
    return this;
  }

  public TestDemoLimits maxInputCharacters(int maxInputCharacters) {
    this.maxInputCharacters = maxInputCharacters;
    return this;
  }

  public TestDemoLimits maxOutputTokensPerCall(int maxOutputTokensPerCall) {
    this.maxOutputTokensPerCall = maxOutputTokensPerCall;
    return this;
  }

  public DemoLimitsConfig build() {
    return new DemoLimitsConfig(
        true,
        firestoreEnabled,
        firestoreProjectId,
        firestoreDatabaseId,
        ipHashSaltEnvironmentVariable,
        dailyRequests,
        hourlyRequestsPerIp,
        concurrentStreams,
        maxInputCharacters,
        MAX_HISTORY_MESSAGES,
        MAX_RAG_CHUNKS,
        maxOutputTokensPerCall);
  }
}
