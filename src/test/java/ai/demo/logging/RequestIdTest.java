package ai.demo.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestIdTest {

  @AfterEach
  void clear() {
    RequestId.clear();
  }

  @Test
  void shouldGenerateShortDistinctIds() {
    String first = RequestId.generate();

    assertTrue(first.matches("[0-9a-f]{8}"), first);
    assertNotEquals(first, RequestId.generate());
  }

  @ParameterizedTest
  @ValueSource(strings = {"8ff9efad", "bruno-a1b2c3d4", "3f2504e0-4f89-11d3-9a0c-0305e82c3301"})
  void shouldAcceptShortSafeIds(String value) {
    assertTrue(RequestId.isValid(value));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"has spaces", "line\nbreak", "semi;colon", "<script>"})
  void shouldRejectUnsafeIds(String value) {
    assertFalse(RequestId.isValid(value));
  }

  @Test
  void shouldRejectTooLongIds() {
    assertTrue(RequestId.isValid("x".repeat(64)));
    assertFalse(RequestId.isValid("x".repeat(65)));
  }

  @Test
  void shouldKeepCurrentIdUntilCleared() {
    assertEquals(Optional.empty(), RequestId.current());

    RequestId.set("8ff9efad");

    assertEquals(Optional.of("8ff9efad"), RequestId.current());
    assertEquals("8ff9efad", RequestId.currentOrGenerate());

    RequestId.clear();

    assertEquals(Optional.empty(), RequestId.current());
    assertTrue(RequestId.currentOrGenerate().matches("[0-9a-f]{8}"));
  }
}
