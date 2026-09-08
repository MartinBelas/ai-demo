package ai.demo.client.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code temperature} is omitted from the request when the model does not support it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiRequest(
    String model,
    List<OpenAiInputMessage> input,
    boolean stream,
    Double temperature,
    @JsonProperty("max_output_tokens") int maxOutputTokens,
    boolean store) {}
