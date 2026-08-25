package ai.demo.api;

import ai.demo.config.AvailableLlmProvider;
import java.util.List;

record LlmProvidersResponse(List<LlmProviderResponse> providers) {

  static LlmProvidersResponse from(List<AvailableLlmProvider> providers) {
    return new LlmProvidersResponse(providers.stream().map(LlmProviderResponse::from).toList());
  }
}
