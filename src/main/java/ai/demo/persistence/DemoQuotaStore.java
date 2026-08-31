package ai.demo.persistence;

import ai.demo.exception.DemoLimitException;

/** Atomic public-demo quota storage. */
public interface DemoQuotaStore extends AutoCloseable {

  Reservation reserve(String clientHash, boolean streaming) throws DemoLimitException;

  void recordUsage(Reservation reservation, int totalTokens);

  void release(Reservation reservation);

  record Reservation(String period, String clientHash, boolean streaming) {}

  @Override
  default void close() {}
}
