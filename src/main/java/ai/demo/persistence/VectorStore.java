package ai.demo.persistence;

import ai.demo.model.rag.EmbeddedChunk;
import ai.demo.model.rag.Embedding;
import ai.demo.model.rag.RagSource;
import java.util.List;

public interface VectorStore {
  /** Publishes a complete index atomically; failures retain the previous index. */
  void replaceAll(List<EmbeddedChunk> chunks);

  List<RagSource> search(Embedding query, int limit, double minimumScore);
}
