package ai.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.agent.AgentEvent;
import ai.demo.agent.ContentEvent;
import ai.demo.agent.ThinkingEvent;
import ai.demo.agent.TokenUsageEvent;
import ai.demo.client.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class TokenMeterTest {

  @Test
  void shouldStartWithoutTokensAndUncharged() {
    TokenMeter meter = new TokenMeter();

    assertEquals(0, meter.totalTokens());
    assertFalse(meter.charged());
  }

  @Test
  void shouldSumTokensOfEveryUsageEvent() {
    TokenMeter meter = new TokenMeter();
    Consumer<AgentEvent> events = meter.observe(event -> {});

    events.accept(new TokenUsageEvent(new TokenUsage(100, 20)));
    events.accept(new TokenUsageEvent(new TokenUsage(30, 5)));

    assertEquals(155, meter.totalTokens());
  }

  @Test
  void shouldNotPassUsageEventsOnButForwardEveryOtherEvent() {
    TokenMeter meter = new TokenMeter();
    List<AgentEvent> forwarded = new ArrayList<>();
    Consumer<AgentEvent> events = meter.observe(forwarded::add);
    ThinkingEvent thinking = new ThinkingEvent("Checking");
    ContentEvent content = new ContentEvent("Hello!");

    events.accept(thinking);
    events.accept(new TokenUsageEvent(new TokenUsage(1, 1)));
    events.accept(content);

    assertEquals(List.of(thinking, content), forwarded);
    assertEquals(2, meter.totalTokens());
  }

  @Test
  void shouldRememberThatUsageWasCharged() {
    TokenMeter meter = new TokenMeter();

    meter.markCharged();

    assertTrue(meter.charged());
  }

  @Test
  void shouldKeepCountingTokensAfterMarkedCharged() {
    TokenMeter meter = new TokenMeter();
    Consumer<AgentEvent> events = meter.observe(event -> {});

    events.accept(new TokenUsageEvent(new TokenUsage(10, 0)));
    meter.markCharged();
    events.accept(new TokenUsageEvent(new TokenUsage(5, 0)));

    assertEquals(15, meter.totalTokens());
    assertTrue(meter.charged());
  }
}
