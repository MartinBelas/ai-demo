package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.exception.RagException;
import ai.demo.model.rag.EmbeddedChunk;
import ai.demo.model.rag.Embedding;
import ai.demo.model.rag.RagSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

  private final InMemoryVectorStore store = new InMemoryVectorStore();

  @Test
  void shouldReturnEmptyWhenNothingIndexed() {
    assertEquals(List.of(), store.search(new Embedding(List.of(1.0, 0.0)), 5, -1));
  }

  @Test
  void shouldOrderResultsByDescendingSimilarity() {
    store.replaceAll(
        List.of(chunk("far", 0.0, 1.0), chunk("close", 1.0, 1.0), chunk("exact", 1.0, 0.0)));

    List<RagSource> results = store.search(new Embedding(List.of(1.0, 0.0)), 10, -1);

    assertEquals(List.of("exact", "close", "far"), ids(results));
  }

  @Test
  void shouldFilterByMinimumScore() {
    store.replaceAll(List.of(chunk("far", 0.0, 1.0), chunk("exact", 1.0, 0.0)));

    List<RagSource> results = store.search(new Embedding(List.of(1.0, 0.0)), 10, 0.5);

    assertEquals(List.of("exact"), ids(results));
  }

  @Test
  void shouldLimitResultCount() {
    store.replaceAll(List.of(chunk("a", 1.0, 0.0), chunk("b", 1.0, 0.0)));

    assertEquals(1, store.search(new Embedding(List.of(1.0, 0.0)), 1, -1).size());
  }

  @Test
  void shouldReturnEmptyForNonPositiveLimit() {
    store.replaceAll(List.of(chunk("a", 1.0, 0.0)));

    assertEquals(List.of(), store.search(new Embedding(List.of(1.0, 0.0)), 0, -1));
  }

  @Test
  void shouldBreakScoreTiesByStableSourceId() {
    store.replaceAll(List.of(chunk("z", 1.0, 0.0), chunk("a", 1.0, 0.0)));

    List<RagSource> results = store.search(new Embedding(List.of(1.0, 0.0)), 10, -1);

    assertEquals(List.of("a", "z"), ids(results));
  }

  @Test
  void shouldRetainPreviousIndexWhenReplaceAllFails() {
    store.replaceAll(List.of(chunk("kept", 1.0, 0.0)));

    EmbeddedChunk mismatched =
        new EmbeddedChunk(source("bad"), new Embedding(List.of(1.0, 0.0, 0.0)));
    List<EmbeddedChunk> invalidReplacement = List.of(chunk("kept", 1.0, 0.0), mismatched);

    assertThrows(RagException.class, () -> store.replaceAll(invalidReplacement));

    List<RagSource> results = store.search(new Embedding(List.of(1.0, 0.0)), 10, -1);
    assertTrue(ids(results).contains("kept"));
    assertEquals(1, results.size());
  }

  private List<String> ids(List<RagSource> sources) {
    return sources.stream().map(RagSource::id).toList();
  }

  private EmbeddedChunk chunk(String id, double x, double y) {
    return new EmbeddedChunk(source(id), new Embedding(List.of(x, y)));
  }

  private RagSource source(String id) {
    return new RagSource(id, "doc.txt", 1, "text " + id);
  }
}
