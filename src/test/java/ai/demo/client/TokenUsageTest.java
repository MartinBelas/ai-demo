package ai.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenUsageTest {

  @Test
  void shouldCalculateTotalTokens() {

    TokenUsage usage = new TokenUsage(100, 50);

    assertEquals(150, usage.totalTokens());
  }
}
