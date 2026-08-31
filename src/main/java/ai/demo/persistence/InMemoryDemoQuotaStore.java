package ai.demo.persistence;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.DemoLimitException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local quota store for development and deterministic tests. */
public final class InMemoryDemoQuotaStore implements DemoQuotaStore {

  private final DemoLimitsConfig limits;
  private final Clock clock;
  private final ConcurrentMap<String, AtomicInteger> daily = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> hourlyByClient = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> tokens = new ConcurrentHashMap<>();
  private final AtomicInteger activeStreams = new AtomicInteger();

  public InMemoryDemoQuotaStore(DemoLimitsConfig limits) {
    this(limits, Clock.systemUTC());
  }

  InMemoryDemoQuotaStore(DemoLimitsConfig limits, Clock clock) {
    this.limits = limits;
    this.clock = clock;
  }

  @Override
  public synchronized Reservation reserve(String clientHash, boolean streaming) {
    String day = LocalDate.now(clock).format(DateTimeFormatter.ISO_DATE);
    String hour =
        day + "T" + String.format("%02d", clock.instant().atZone(ZoneOffset.UTC).getHour());
    AtomicInteger dayCount = daily.computeIfAbsent(day, ignored -> new AtomicInteger());
    AtomicInteger clientCount =
        hourlyByClient.computeIfAbsent(hour + ':' + clientHash, ignored -> new AtomicInteger());
    if (dayCount.get() >= limits.dailyRequests()
        || clientCount.get() >= limits.hourlyRequestsPerIp()) {
      throw new DemoLimitException();
    }
    if (streaming && activeStreams.get() >= limits.concurrentStreams())
      throw new DemoLimitException();
    dayCount.incrementAndGet();
    clientCount.incrementAndGet();
    if (streaming) activeStreams.incrementAndGet();
    return new Reservation(day, clientHash, streaming);
  }

  @Override
  public void recordUsage(Reservation reservation, int totalTokens) {
    tokens
        .computeIfAbsent(reservation.period(), ignored -> new AtomicLong())
        .addAndGet(Math.max(0, totalTokens));
  }

  @Override
  public void release(Reservation reservation) {
    if (reservation.streaming()) activeStreams.updateAndGet(value -> Math.max(0, value - 1));
  }
}
