package ai.demo.exception;

/** Classifies why communication with an LLM provider failed, for user-facing messaging. */
public enum LlmErrorCategory {
  RATE_LIMIT,
  QUOTA_EXHAUSTED,
  AUTHENTICATION,
  OTHER
}
