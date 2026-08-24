package ai.demo.client.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiRequest(
    String model,
    List<OpenAiInputMessage> input,
    boolean stream,
    double temperature,
    @JsonProperty("max_output_tokens") int maxOutputTokens,
    boolean store) {}
