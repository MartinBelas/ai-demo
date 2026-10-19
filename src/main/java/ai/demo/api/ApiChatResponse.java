package ai.demo.api;

import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.WebSource;
import ai.demo.model.rag.RagSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

record ApiChatResponse(
    String answer,
    String model,
    ApiTokenUsage tokenUsage,
    long durationMs,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RagSource> sources,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<WebSource> webSources) {

  static ApiChatResponse from(ChatResponse response) {
    return new ApiChatResponse(
        response.answer(),
        response.model(),
        ApiTokenUsage.from(response.tokenUsage()),
        response.durationMs(),
        response.sources(),
        response.webSources());
  }
}
