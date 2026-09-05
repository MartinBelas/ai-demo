package ai.demo.config;

/** Public demo safeguards. */
public record DemoLimitsConfig(
    boolean enabled,
    boolean firestoreEnabled,
    String firestoreProjectId,
    String firestoreDatabaseId,
    String ipHashSaltEnvironmentVariable,
    int dailyRequests,
    int hourlyRequestsPerIp,
    int concurrentStreams,
    int maxInputCharacters,
    int maxHistoryMessages,
    int maxRagChunks,
    int maxOutputTokensPerCall) {

  public DemoLimitsConfig {
    positive(dailyRequests, "dailyRequests");
    positive(hourlyRequestsPerIp, "hourlyRequestsPerIp");
    positive(concurrentStreams, "concurrentStreams");
    positive(maxInputCharacters, "maxInputCharacters");
    positive(maxHistoryMessages, "maxHistoryMessages");
    positive(maxRagChunks, "maxRagChunks");
    positive(maxOutputTokensPerCall, "maxOutputTokensPerCall");
    if (firestoreEnabled
        && (ipHashSaltEnvironmentVariable == null || ipHashSaltEnvironmentVariable.isBlank())) {
      throw new IllegalArgumentException("ipHashSaltEnvironmentVariable must not be blank");
    }
  }

  public static DemoLimitsConfig disabled() {
    return new DemoLimitsConfig(
        false, false, "", "(default)", "DEMO_IP_HASH_SALT", 1000, 20, 5, 20000, 10, 5, 1500);
  }

  private static void positive(int value, String name) {
    if (value <= 0) throw new IllegalArgumentException(name + " must be greater than zero");
  }
}
