package ai.demo.service;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.model.app.AppMetrics;
import ai.demo.persistence.DemoQuotaStore;
import java.time.Clock;
import java.time.LocalDate;

/** Serves only explicitly selected public aggregates; caches storage reads for ten seconds. */
public final class AppStatusService {
  private final DemoQuotaStore store;
  private final DemoLimitsConfig limits;
  private final Clock clock;
  private final long startedNanos = System.nanoTime();
  private AppMetrics cached;
  private String cachedPeriod;
  private long cachedAt;

  public AppStatusService(DemoQuotaStore store, DemoLimitsConfig limits) {
    this(store, limits, Clock.systemUTC());
  }

  AppStatusService(DemoQuotaStore store, DemoLimitsConfig limits, Clock clock) {
    this.store = store;
    this.limits = limits;
    this.clock = clock;
  }

  public synchronized Status status() {
    String period = LocalDate.now(clock).toString();
    long now = System.nanoTime();
    AppMetrics metrics = AppMetrics.empty();
    if (limits.enabled()) {
      if (cached == null || !period.equals(cachedPeriod) || now - cachedAt >= 10_000_000_000L) {
        cached = store.snapshot(period);
        cachedPeriod = period;
        cachedAt = now;
      }
      metrics = cached;
    }
    return new Status(
        limits.enabled(),
        limits.enabled() && limits.firestoreEnabled(),
        period,
        metrics.requests(),
        metrics.tokens(),
        metrics.completed(),
        metrics.failed(),
        metrics.disconnected(),
        metrics.completed() == 0
            ? null
            : (double) metrics.completedDurationMs() / metrics.completed(),
        limits.enabled() ? store.activeStreams() : 0,
        (now - startedNanos) / 1_000_000_000L,
        limits.enabled() ? limits.dailyRequests() : null,
        limits.enabled() ? Math.max(0L, limits.dailyRequests() - metrics.requests()) : null);
  }

  public record Status(
      boolean trackingEnabled,
      boolean persistent,
      String period,
      long requests,
      long tokens,
      long completed,
      long failed,
      long disconnected,
      Double averageDurationMs,
      int activeStreams,
      long uptimeSeconds,
      Integer dailyRequestLimit,
      Long requestsRemaining) {}
}
