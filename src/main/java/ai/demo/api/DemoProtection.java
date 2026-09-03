package ai.demo.api;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.persistence.DemoQuotaStore;
import ai.demo.persistence.DemoQuotaStoreFactory;
import ai.demo.persistence.InMemoryDemoQuotaStore;

/** Dependencies used to protect the public demo endpoints. */
public record DemoProtection(DemoLimitsConfig limits, DemoQuotaStore quotaStore, String ipHashSalt) {

  private static final String LOCAL_DEVELOPMENT_SALT = "local-development";

  public static DemoProtection localDevelopment() {
    DemoLimitsConfig limits = DemoLimitsConfig.disabled();
    return new DemoProtection(limits, new InMemoryDemoQuotaStore(limits), LOCAL_DEVELOPMENT_SALT);
  }

  public static DemoProtection configured(DemoLimitsConfig limits, String ipHashSalt) {
    return new DemoProtection(limits, DemoQuotaStoreFactory.create(limits), ipHashSalt);
  }
}
