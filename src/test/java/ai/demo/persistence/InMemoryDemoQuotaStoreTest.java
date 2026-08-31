package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.DemoLimitException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryDemoQuotaStoreTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void shouldEnforceDailyAndPerClientRequestLimits() {
    DemoLimitsConfig limits = limits(2, 1, 2);
    InMemoryDemoQuotaStore store = new InMemoryDemoQuotaStore(limits, CLOCK);

    store.reserve("client-a", false);
    assertThrows(DemoLimitException.class, () -> store.reserve("client-a", false));
    assertDoesNotThrow(() -> store.reserve("client-b", false));
    assertThrows(DemoLimitException.class, () -> store.reserve("client-c", false));
  }

  @Test
  void shouldReleaseConcurrentStreamSlot() {
    InMemoryDemoQuotaStore store = new InMemoryDemoQuotaStore(limits(10, 10, 1), CLOCK);
    DemoQuotaStore.Reservation first = store.reserve("client-a", true);

    assertThrows(DemoLimitException.class, () -> store.reserve("client-b", true));
    store.release(first);
    assertDoesNotThrow(() -> store.reserve("client-b", true));
  }

  private DemoLimitsConfig limits(int daily, int hourly, int streams) {
    return new DemoLimitsConfig(
        true, false, "", "(default)", "DEMO_IP_HASH_SALT", daily, hourly, streams, 100, 10, 5, 50);
  }
}
