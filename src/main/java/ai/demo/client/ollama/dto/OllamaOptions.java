package ai.demo.client.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OllamaOptions(
    double temperature,
    @JsonProperty("num_predict") int maxOutputTokens,
    @JsonProperty("num_ctx") int contextWindow,
    @JsonProperty("repeat_penalty") double repeatPenalty) {}
