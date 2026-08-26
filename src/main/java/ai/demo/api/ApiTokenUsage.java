package ai.demo.api;

import ai.demo.client.TokenUsage;

record ApiTokenUsage(int promptTokens, int completionTokens, int totalTokens) {

  static ApiTokenUsage from(TokenUsage usage) {
    return new ApiTokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
  }
}
