package ai.demo.api;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.ApiRequestException;
import ai.demo.model.chat.Conversation;
import ai.demo.persistence.DemoQuotaStore;
import io.javalin.http.Context;

final class DemoRequestGate {

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
}
