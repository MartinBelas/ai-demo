package ai.demo.api;

import ai.demo.model.chat.ChatResponse;

record ApiChatResponse(String answer, String model, ApiTokenUsage tokenUsage, long durationMs) {

  static ApiChatResponse from(ChatResponse response) {
    return new ApiChatResponse(
        response.answer(),
        response.model(),
        ApiTokenUsage.from(response.tokenUsage()),
        response.durationMs());
  }
}
