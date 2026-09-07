package ai.demo.model.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.RagException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingTest {

  private static final double DELTA = 1e-9;

  @Test
  void shouldNormalizeValuesToUnitLength() {
    Embedding embedding = new Embedding(List.of(3.0, 4.0));

    assertEquals(0.6, embedding.values().get(0), DELTA);
    assertEquals(0.8, embedding.values().get(1), DELTA);
  }

  @Test
  void shouldReturnMaximumSimilarityForIdenticalDirection() {
    Embedding query = new Embedding(List.of(1.0, 0.0));
    Embedding same = new Embedding(List.of(2.0, 0.0));

    assertEquals(1.0, query.similarity(same), DELTA);
  }

  @Test
  void shouldReturnZeroSimilarityForOrthogonalVectors() {
    Embedding a = new Embedding(List.of(1.0, 0.0));
    Embedding b = new Embedding(List.of(0.0, 1.0));

    assertEquals(0.0, a.similarity(b), DELTA);
  }

  @Test
  void shouldRejectDimensionMismatch() {
    Embedding a = new Embedding(List.of(1.0, 0.0));
    Embedding b = new Embedding(List.of(1.0, 0.0, 0.0));

    assertThrows(RagException.class, () -> a.similarity(b));
  }

  @Test
  void shouldRejectEmptyValues() {
    assertThrows(RagException.class, () -> new Embedding(List.of()));
  }

  @Test
  void shouldRejectNullValues() {
    assertThrows(RagException.class, () -> new Embedding(null));
  }

  @Test
  void shouldRejectNonFiniteValues() {
    List<Double> withNan = List.of(1.0, Double.NaN);
    List<Double> withInfinity = List.of(1.0, Double.POSITIVE_INFINITY);

    assertThrows(RagException.class, () -> new Embedding(withNan));
    assertThrows(RagException.class, () -> new Embedding(withInfinity));
  }

  @Test
  void shouldRejectZeroVector() {
    List<Double> zeroVector = List.of(0.0, 0.0);

    assertThrows(RagException.class, () -> new Embedding(zeroVector));
  }
}
