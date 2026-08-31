package ai.demo.persistence;

import ai.demo.config.DemoLimitsConfig;

/** Selects the configured public-demo quota persistence implementation. */
public final class DemoQuotaStoreFactory {

  private DemoQuotaStoreFactory() {}

  public static DemoQuotaStore create(DemoLimitsConfig limits) {
    if (limits.firestoreEnabled()) {
      return new FirestoreDemoQuotaStore(limits);
    }
    return new InMemoryDemoQuotaStore(limits);
  }
}
