package ai.demo.persistence;

import ai.demo.config.RagConfig;
import ai.demo.exception.RagException;
import ai.demo.model.rag.RagDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Explicit resource manifest makes corpus contents reproducible in the packaged JAR. */
public final class BundledRagDocuments {
  private static final Pattern DOCUMENT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+\\.(txt|md)");

  private final RagConfig config;

  public BundledRagDocuments(RagConfig config) {
    this.config = config;
  }

  public List<RagDocument> load() {
    List<String> names = read("documents.list", 8192).lines().filter(s -> !s.isBlank()).toList();
    if (names.size() > config.maxDocuments()) throw new RagException("Too many bundled documents");
    List<RagDocument> result = new ArrayList<>();
    long total = 0;
    for (String name : names) {
      if (!DOCUMENT_NAME_PATTERN.matcher(name).matches())
        throw new RagException("Invalid document name");
      String text = read(name, config.maxDocumentBytes());
      total += text.getBytes(StandardCharsets.UTF_8).length;
      if (total > config.maxCorpusBytes()) throw new RagException("Corpus size limit exceeded");
      result.add(new RagDocument(name, text));
    }
    return List.copyOf(result);
  }

  private String read(String name, int limit) {
    try (var input = getClass().getResourceAsStream("/rag/" + name)) {
      if (input == null) throw new RagException("Missing bundled RAG document");
      byte[] bytes = input.readNBytes(limit);
      if (input.read() != -1) throw new RagException("Bundled document exceeds size limit");
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    } catch (IOException e) {
      throw new RagException("Unable to read bundled RAG document", e);
    }
  }
}
