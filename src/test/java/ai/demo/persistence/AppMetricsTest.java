package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.*;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.model.app.AppOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppMetricsTest {
  @Test
  void aggregatesAcceptedRequestsAndTerminalOutcomesWithoutClientData() {
    var clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    try (var store = new InMemoryDemoQuotaStore(DemoLimitsConfig.disabled(), clock)) {
      var first = store.reserve("private-client", true);
      store.recordUsage(first, 42);
      store.recordOutcome(first, AppOutcome.COMPLETED, 120);
      store.release(first);
      var second = store.reserve("another-private-client", false);
      store.recordOutcome(second, AppOutcome.FAILED, 900);
      var third = store.reserve("third-client", true);
      store.recordOutcome(third, AppOutcome.DISCONNECTED, 500);
      store.release(third);

      var metrics = store.snapshot("2026-09-03");
      assertEquals(3, metrics.requests());
      assertEquals(42, metrics.tokens());
      assertEquals(1, metrics.completed());
      assertEquals(1, metrics.failed());
      assertEquals(1, metrics.disconnected());
      assertEquals(120, metrics.completedDurationMs());
      assertEquals(0, store.activeStreams());
      assertEquals(0, store.snapshot("2026-09-04").requests());
    }
  }
}
