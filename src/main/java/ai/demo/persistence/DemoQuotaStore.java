package ai.demo.persistence;

import ai.demo.exception.DemoLimitException;
import ai.demo.model.app.AppMetrics;
import ai.demo.model.app.AppOutcome;

/** Atomic public-demo quota storage. */
public interface DemoQuotaStore extends AutoCloseable {

  Reservation reserve(String clientHash, boolean streaming) throws DemoLimitException;

  void recordUsage(Reservation reservation, int totalTokens);

  void release(Reservation reservation);

  void recordOutcome(Reservation reservation, AppOutcome outcome, long durationMs);

  AppMetrics snapshot(String period);

  int activeStreams();

  record Reservation(String period, String clientHash, boolean streaming) {}

  @Override
  default void close() {}
}
