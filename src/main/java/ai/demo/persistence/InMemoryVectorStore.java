package ai.demo.persistence;

import ai.demo.exception.RagException;
import ai.demo.model.rag.EmbeddedChunk;
import ai.demo.model.rag.Embedding;
import ai.demo.model.rag.RagSource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryVectorStore implements VectorStore {
  private final AtomicReference<List<EmbeddedChunk>> chunks = new AtomicReference<>(List.of());

  @Override
  public void replaceAll(List<EmbeddedChunk> values) {
    List<EmbeddedChunk> snapshot = List.copyOf(values);
    if (!snapshot.isEmpty()) {
      int dimensions = snapshot.getFirst().embedding().dimensions();
      if (snapshot.stream().anyMatch(chunk -> chunk.embedding().dimensions() != dimensions)) {
        // Reject before publishing: a mixed index would fail on every later search.
        throw new RagException("Embedding dimensions differ");
      }
    }
    chunks.set(snapshot);
  }

  @Override
  public List<RagSource> search(Embedding query, int limit, double minimumScore) {
    if (limit <= 0) return List.of();
    return chunks.get().stream()
        .map(chunk -> new Match(chunk.source(), query.similarity(chunk.embedding())))
        .filter(match -> match.score() >= minimumScore)
        .sorted(
            Comparator.comparingDouble(Match::score)
                .reversed()
                .thenComparing(match -> match.source().id()))
        .limit(limit)
        .map(Match::source)
        .toList();
  }

  private record Match(RagSource source, double score) {}
}
