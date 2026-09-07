package ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.demo.client.EmbeddingClient;
import ai.demo.config.EmbeddingProvider;
import ai.demo.config.RagConfig;
import ai.demo.exception.ApiRequestException;
import ai.demo.exception.RagDisabledException;
import ai.demo.exception.RagException;
import ai.demo.model.rag.Embedding;
import ai.demo.model.rag.RagDocument;
import ai.demo.model.rag.RagSource;
import ai.demo.persistence.InMemoryVectorStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RagServiceTest {

  @Test
  void shouldRejectQueryWhenDisabled() {
    RagService service =
        new RagService(
            RagConfig.disabled(), text -> unitEmbedding(), new InMemoryVectorStore(), List::of);

    assertThrows(RagDisabledException.class, () -> service.validateQuery("hello"));
    assertThrows(RagDisabledException.class, () -> service.retrieve("hello"));
  }

  @Test
  void shouldRejectBlankOrOversizedQuery() {
    RagConfig config = config(20, 200, 6, 10);
    RagService service =
        new RagService(config, text -> unitEmbedding(), new InMemoryVectorStore(), List::of);

    assertThrows(ApiRequestException.class, () -> service.validateQuery(" "));
    assertThrows(ApiRequestException.class, () -> service.validateQuery(null));
    assertThrows(
        ApiRequestException.class, () -> service.validateQuery("this text is far too long"));
  }

  @Test
  void shouldIndexCorpusOnceAndReuseItAcrossRetrievals() {
    AtomicInteger embedCalls = new AtomicInteger();
    EmbeddingClient counting =
        text -> {
          embedCalls.incrementAndGet();
          return unitEmbedding();
        };
    RagConfig config = config(20, 4000, 4000, 4000);
    RagService service =
        new RagService(
            config,
            counting,
            new InMemoryVectorStore(),
            () -> List.of(document("a.txt", "hello world")));

    service.retrieve("question one");
    int callsAfterFirstIndex = embedCalls.get();
    service.retrieve("question two");

    assertTrue(callsAfterFirstIndex >= 2, "expected at least one chunk embed and one query embed");
    assertEquals(
        callsAfterFirstIndex + 1, embedCalls.get(), "second call must only embed the query");
  }

  @Test
  void shouldCacheIndexingFailureWithoutRetryingDocumentLoad() {
    AtomicInteger documentLoads = new AtomicInteger();
    RagConfig config = config(20, 4000, 4000, 4000);
    RagService service =
        new RagService(
            config,
            text -> unitEmbedding(),
            new InMemoryVectorStore(),
            () -> {
              documentLoads.incrementAndGet();
              throw new RagException("corpus unavailable");
            });

    assertThrows(RagException.class, () -> service.retrieve("question"));
    assertThrows(RagException.class, () -> service.retrieve("question"));
    assertEquals(1, documentLoads.get());
  }

  @Test
  void shouldTrimSelectedSourcesToContextByteBudget() {
    RagConfig config = config(20, 4000, 8, 4000);
    RagService service =
        new RagService(
            config,
            text -> unitEmbedding(),
            new InMemoryVectorStore(),
            () -> List.of(document("a.txt", "aaaaaaaaaa"), document("b.txt", "bbbbbbbbbb")));

    List<RagSource> sources = service.retrieve("question");

    int totalBytes =
        sources.stream().mapToInt(s -> s.text().getBytes(StandardCharsets.UTF_8).length).sum();
    assertTrue(totalBytes <= 8);
  }

  @Test
  void shouldRejectDuplicateDocumentNamesInCorpus() {
    RagConfig config = config(20, 4000, 4000, 4000);
    RagService service =
        new RagService(
            config,
            text -> unitEmbedding(),
            new InMemoryVectorStore(),
            () -> List.of(document("a.txt", "one"), document("a.txt", "two")));

    assertThrows(RagException.class, () -> service.retrieve("question"));
  }

  @Test
  void shouldRejectDocumentExceedingSizeLimit() {
    RagConfig config = config(4, 4000, 4000, 4000);
    RagService service =
        new RagService(
            config,
            text -> unitEmbedding(),
            new InMemoryVectorStore(),
            () -> List.of(document("a.txt", "this text is longer than four bytes")));

    assertThrows(RagException.class, () -> service.retrieve("question"));
  }

  @Test
  void shouldSplitTextOnWhitespaceBoundaryWithinByteLimit() {
    List<String> chunks = RagService.split("aaaa bbbb cccc", 6);

    for (String chunk : chunks) {
      assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 6);
    }
    assertEquals(List.of("aaaa", "bbbb", "cccc"), chunks);
  }

  @Test
  void shouldSplitWithoutBreakingMultibyteCharacters() {
    String text = "řřřřř"; // 'r with caron', 2 bytes in UTF-8 each
    List<String> chunks = RagService.split(text, 5);

    for (String chunk : chunks) {
      byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
      assertEquals(chunk, new String(bytes, StandardCharsets.UTF_8));
      assertTrue(bytes.length <= 5);
    }
    assertEquals(text, String.join("", chunks));
  }

  @Test
  void shouldDropBlankChunksAndTrimWhitespace() {
    List<String> chunks = RagService.split("  hello   ", 20);

    assertEquals(List.of("hello"), chunks);
  }

  @Test
  void shouldReturnMatchingSourceForValidCorpus() {
    RagConfig config = config(20, 4000, 4000, 4000);
    RagService service =
        new RagService(
            config,
            text -> unitEmbedding(),
            new InMemoryVectorStore(),
            () -> List.of(document("a.txt", "hello")));

    assertFalse(service.retrieve("question").isEmpty());
  }

  private RagConfig config(
      int maxDocumentBytes, int maxCorpusBytes, int maxContextBytes, int maxQueryBytes) {
    return new RagConfig(
        true,
        EmbeddingProvider.OLLAMA,
        "http://localhost:11434",
        "embeddinggemma",
        null,
        maxDocumentBytes,
        20,
        maxCorpusBytes,
        4,
        maxContextBytes,
        maxQueryBytes,
        5,
        -1);
  }

  private RagDocument document(String name, String text) {
    return new RagDocument(name, text);
  }

  private Embedding unitEmbedding() {
    return new Embedding(List.of(1.0, 0.0));
  }
}
