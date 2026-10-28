package ai.demo.exception;

/** Classifies why communication with an LLM provider failed, for user-facing messaging. */
public enum LlmErrorCategory {
  RATE_LIMIT,
  QUOTA_EXHAUSTED,
  AUTHENTICATION,
  OTHER;

  /** Maps the provider-independent HTTP statuses 429, 401 and 403; anything else is OTHER. */
  public static LlmErrorCategory fromHttpStatus(int statusCode) {
    return switch (statusCode) {
      case 429 -> RATE_LIMIT;
      case 401, 403 -> AUTHENTICATION;
      default -> OTHER;
    };
  }
}
