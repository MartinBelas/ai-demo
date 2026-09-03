package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.PersistenceException;
import ai.demo.model.app.AppOutcome;
import ai.demo.persistence.DemoQuotaStore;
import org.junit.jupiter.api.Test;

class DemoRequestGateMetricsTest {
  @Test
  void optionalOutcomeFailureDoesNotChangeTheChatResult() {
    var store = mock(DemoQuotaStore.class);
    var gate = new DemoRequestGate(DemoLimitsConfig.disabled(), store, "");
    var reservation = new DemoQuotaStore.Reservation("2026-09-03", "private", true);
    doThrow(new PersistenceException("offline", null))
        .when(store)
        .recordOutcome(eq(reservation), eq(AppOutcome.COMPLETED), anyLong());
    assertDoesNotThrow(
        () -> gate.recordOutcome(reservation, AppOutcome.COMPLETED, System.nanoTime()));
    verify(store).recordOutcome(eq(reservation), eq(AppOutcome.COMPLETED), anyLong());
  }
}
