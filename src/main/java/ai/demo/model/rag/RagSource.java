package ai.demo.model.rag;

/** Text actually supplied to the model, with a stable citation identifier. */
public record RagSource(String id, String document, int chunk, String text) {}
