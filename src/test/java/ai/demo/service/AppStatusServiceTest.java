package ai.demo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.model.app.AppMetrics;
import ai.demo.persistence.DemoQuotaStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppStatusServiceTest {
  private final DemoQuotaStore store = mock(DemoQuotaStore.class);

  @Test
  void disabledTrackingDoesNotReadStorageOrClaimZeroUsageIsMeasured() {
    var status = new AppStatusService(store, DemoLimitsConfig.disabled()).status();
    assertFalse(status.trackingEnabled());
    assertFalse(status.persistent());
    assertNull(status.averageDurationMs());
    assertNull(status.requestsRemaining());
    verifyNoInteractions(store);
  }

  @Test
  void cachesDailyAggregatesButRefreshesOnUtcDateChange() {
    Clock clock = mock(Clock.class);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(clock.instant()).thenReturn(Instant.parse("2026-09-03T23:59:59Z"));
    when(store.snapshot("2026-09-03")).thenReturn(new AppMetrics(3, 42, 2, 1, 0, 300));
    when(store.snapshot("2026-09-04")).thenReturn(AppMetrics.empty());
    var limits =
        new DemoLimitsConfig(true, true, "project", "db", "SALT", 200, 20, 5, 20000, 10, 5, 1000);
    var service = new AppStatusService(store, limits, clock);
    var first = service.status();
    assertEquals(150.0, first.averageDurationMs());
    assertEquals(197L, first.requestsRemaining());
    assertTrue(first.persistent());
    service.status();
    verify(store, times(1)).snapshot("2026-09-03");
    when(clock.instant()).thenReturn(Instant.parse("2026-09-04T00:00:00Z"));
    var next = service.status();
    assertEquals("2026-09-04", next.period());
    assertEquals(0, next.requests());
    assertNull(next.averageDurationMs());
  }
}
