package ai.demo.config;

/** LLM provider that is configured and usable in the current deployment. */
public record AvailableLlmProvider(LlmProvider id, String model) {}
