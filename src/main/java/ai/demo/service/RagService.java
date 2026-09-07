package ai.demo.service;

import ai.demo.client.EmbeddingClient;
import ai.demo.config.RagConfig;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.RagDisabledException;
import ai.demo.exception.RagException;
import ai.demo.model.rag.EmbeddedChunk;
import ai.demo.model.rag.RagDocument;
import ai.demo.model.rag.RagSource;
import ai.demo.persistence.VectorStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** One shared, immutable corpus per process. Embeddings are prepared once, on first retrieval. */
public final class RagService {
  private static final Pattern DOCUMENT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+\\.(txt|md)");

  private final RagConfig config;
  private final EmbeddingClient embeddings;
  private final VectorStore store;
  private final Supplier<List<RagDocument>> documents;
  private volatile boolean indexed;
  private RagException indexingFailure;

  public RagService(
      RagConfig config,
      EmbeddingClient embeddings,
      VectorStore store,
      Supplier<List<RagDocument>> documents) {
    this.config = config;
    this.embeddings = embeddings;
    this.store = store;
    this.documents = documents;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public void validateQuery(String query) {
    if (!enabled()) throw new RagDisabledException();
    if (query == null || query.isBlank() || bytes(query) > config.maxQueryBytes()) {
      throw new ApiRequestException(
          "messages", "The RAG question exceeds the configured size limit or is empty.");
    }
  }

  public List<RagSource> retrieve(String query) {
    validateQuery(query);
    ensureIndexed();
    var candidates =
        store.search(embeddings.embed(query), config.maxChunks(), config.minimumScore());
    List<RagSource> selected = new ArrayList<>();
    int used = 0;
    for (RagSource source : candidates) {
      int size = bytes(source.text());
      if (size > config.maxContextBytes() - used) continue;
      selected.add(source);
      used += size;
    }
    return List.copyOf(selected);
  }

  private synchronized void ensureIndexed() {
    if (indexed) return;
    // Do not repeatedly spend provider calls on a broken corpus on every public request.
    if (indexingFailure != null) throw indexingFailure;
    try {
      List<RagDocument> corpus = documents.get();
      validateCorpus(corpus);
      store.replaceAll(embedAll(toSources(corpus)));
      indexed = true;
    } catch (RagException e) {
      indexingFailure = e;
      throw e;
    }
  }

  private List<RagSource> toSources(List<RagDocument> corpus) {
    List<RagSource> sources = new ArrayList<>();
    for (RagDocument document : corpus) {
      List<String> chunks = split(document.text(), config.maxChunkBytes());
      for (int i = 0; i < chunks.size(); i++) {
        sources.add(
            new RagSource(document.name() + ":" + (i + 1), document.name(), i + 1, chunks.get(i)));
      }
    }
    return sources;
  }

  /** Embeds every chunk concurrently so one slow provider call does not serialize the rest. */
  private List<EmbeddedChunk> embedAll(List<RagSource> sources) {
    List<Future<EmbeddedChunk>> futures = new ArrayList<>();
    try (ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
      for (RagSource source : sources) {
        futures.add(
            virtualThreads.submit(
                () -> new EmbeddedChunk(source, embeddings.embed(source.text()))));
      }
    }
    List<EmbeddedChunk> index = new ArrayList<>();
    for (Future<EmbeddedChunk> future : futures) {
      index.add(resolve(future));
    }
    return index;
  }

  private EmbeddedChunk resolve(Future<EmbeddedChunk> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RagException("Embedding indexing was interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RagException ragException) throw ragException;
      throw new RagException("Embedding indexing failed", e.getCause());
    }
  }

  private void validateCorpus(List<RagDocument> corpus) {
    if (corpus == null || corpus.isEmpty() || corpus.size() > config.maxDocuments()) {
      throw new RagException("Invalid corpus document count");
    }
    long total = 0;
    var names = new HashSet<String>();
    for (RagDocument document : corpus) {
      if (document == null
          || document.name() == null
          || !DOCUMENT_NAME_PATTERN.matcher(document.name()).matches()
          || !names.add(document.name())
          || document.text() == null
          || document.text().isBlank()) {
        throw new RagException("Invalid corpus document");
      }
      int size = bytes(document.text());
      if (size > config.maxDocumentBytes()) throw new RagException("Document size limit exceeded");
      total += size;
      if (total > config.maxCorpusBytes()) throw new RagException("Corpus size limit exceeded");
    }
  }

  /** Bounds UTF-8 size without splitting a Unicode code point; prefers whitespace boundaries. */
  static List<String> split(String text, int limit) {
    String normalized = normalize(text);
    List<String> result = new ArrayList<>();
    int start = 0;
    while (start < normalized.length()) {
      int end = findChunkEnd(normalized, start, limit);
      String chunk = normalized.substring(start, end).strip();
      if (!chunk.isBlank()) result.add(chunk);
      start = end;
    }
    return List.copyOf(result);
  }

  private static String normalize(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "").strip();
  }

  /**
   * Finds the end of the next chunk starting at {@code start}: as many whole code points as fit in
   * {@code limit} UTF-8 bytes, preferring to end at a whitespace boundary when one exists.
   */
  private static int findChunkEnd(String text, int start, int limit) {
    int end = start;
    int lastBoundary = -1;
    int used = 0;
    while (end < text.length()) {
      int cp = text.codePointAt(end);
      int size = utf8Size(cp);
      if (used + size > limit) break;
      used += size;
      end += Character.charCount(cp);
      if (Character.isWhitespace(cp)) lastBoundary = end;
    }
    return end < text.length() && lastBoundary > start ? lastBoundary : end;
  }

  private static int utf8Size(int codePoint) {
    if (codePoint <= 0x7f) return 1;
    if (codePoint <= 0x7ff) return 2;
    if (codePoint <= 0xffff) return 3;
    return 4;
  }

  private static int bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
