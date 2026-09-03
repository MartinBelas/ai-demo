package ai.demo.model.app;

/** Daily aggregate values, without client identifiers or conversation data. */
public record AppMetrics(
    long requests,
    long tokens,
    long completed,
    long failed,
    long disconnected,
    long completedDurationMs) {
  public static AppMetrics empty() {
    return new AppMetrics(0, 0, 0, 0, 0, 0);
  }
}
