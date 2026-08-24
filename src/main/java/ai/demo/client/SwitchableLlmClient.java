package ai.demo.client;

import ai.demo.config.LlmProvider;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.prompt.Prompt;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Delegates requests to a lazily created, runtime-selectable provider client. */
public final class SwitchableLlmClient implements LlmClient, LlmProviderSelector {

  private final Map<LlmProvider, Supplier<LlmClient>> factories;
  private final Map<LlmProvider, LlmClient> clients = new EnumMap<>(LlmProvider.class);
  private volatile LlmProvider activeProvider;

  public SwitchableLlmClient(
      LlmProvider initialProvider, Map<LlmProvider, Supplier<LlmClient>> factories) {
    this.activeProvider = Objects.requireNonNull(initialProvider);
    this.factories = Map.copyOf(factories);
    client(initialProvider);
  }

  @Override
  public LlmResponse chat(Prompt prompt) {
    return client(activeProvider).chat(prompt);
  }

  @Override
  public StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer) {
    return client(activeProvider).stream(prompt, consumer);
  }

  @Override
  public LlmProvider activeProvider() {
    return activeProvider;
  }

  @Override
  public void switchTo(LlmProvider provider) {
    Objects.requireNonNull(provider);
    client(provider);
    activeProvider = provider;
  }

  private synchronized LlmClient client(LlmProvider provider) {
    LlmClient existing = clients.get(provider);
    if (existing != null) return existing;

    Supplier<LlmClient> factory = factories.get(provider);
    if (factory == null) {
      throw new IllegalArgumentException("Provider is not configured: " + provider);
    }
    LlmClient created = Objects.requireNonNull(factory.get());
    clients.put(provider, created);
    return created;
  }
}
