package ai.demo.client;

import ai.demo.config.LlmProvider;

public interface LlmProviderSelector {
  LlmProvider activeProvider();

  void switchTo(LlmProvider provider);
}
