package ai.demo.api;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.ApiRequestException;
import ai.demo.model.app.AppOutcome;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.DemoQuotaStore;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DemoRequestGate {
  private static final Logger log = LoggerFactory.getLogger(DemoRequestGate.class);

  private final DemoLimitsConfig limits;
  private final DemoQuotaStore quotaStore;
  private final ClientHashProvider clientHashProvider;

  DemoRequestGate(DemoLimitsConfig limits, DemoQuotaStore quotaStore, String ipHashSalt) {
    this.limits = limits;
    this.quotaStore = quotaStore;
    this.clientHashProvider = limits.enabled() ? new ClientHashProvider(ipHashSalt) : null;
  }

  void validate(Conversation conversation) {
    if (!limits.enabled()) return;
    if (conversation.messages().size() > limits.maxHistoryMessages()) {
      throw new ApiRequestException("messages", "Too many chat history messages.");
    }
    int characters =
        conversation.messages().stream().mapToInt(message -> message.content().length()).sum();
    if (characters > limits.maxInputCharacters()) {
      throw new ApiRequestException("messages", "Chat input is too long.");
    }
  }

  DemoQuotaStore.Reservation reserve(Context context, boolean streaming) {
    if (!limits.enabled()) return null;
    return quotaStore.reserve(clientHashProvider.hash(context), streaming);
  }

  void recordUsage(DemoQuotaStore.Reservation reservation, int totalTokens) {
    if (reservation != null) quotaStore.recordUsage(reservation, totalTokens);
  }

  void release(DemoQuotaStore.Reservation reservation) {
    if (reservation != null) quotaStore.release(reservation);
  }

  void recordOutcome(DemoQuotaStore.Reservation reservation, AppOutcome outcome, long started) {
    if (reservation == null) return;
    try {
      quotaStore.recordOutcome(reservation, outcome, (System.nanoTime() - started) / 1_000_000L);
    } catch (RuntimeException e) {
      // Optional telemetry must not turn a delivered answer into an error or leak identifiers.
      log.warn("Unable to record aggregate demo outcome");
    }
  }
}
