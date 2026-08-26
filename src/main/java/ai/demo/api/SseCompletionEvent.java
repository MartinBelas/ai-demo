package ai.demo.api;

import ai.demo.model.chat.ChatResponse;

record SseCompletionEvent(String model, ApiTokenUsage tokenUsage, long durationMs) {

  static SseCompletionEvent from(ChatResponse response) {
    return new SseCompletionEvent(
        response.model(), ApiTokenUsage.from(response.tokenUsage()), response.durationMs());
  }
}
