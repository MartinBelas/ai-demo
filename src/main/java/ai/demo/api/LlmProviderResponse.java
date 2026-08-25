package ai.demo.api;

import ai.demo.config.AvailableLlmProvider;

record LlmProviderResponse(String id, String model) {

  static LlmProviderResponse from(AvailableLlmProvider provider) {
    return new LlmProviderResponse(provider.id().name(), provider.model());
  }
}
