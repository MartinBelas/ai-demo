package ai.demo.client;

import ai.demo.model.rag.Embedding;

/** Converts text into one fixed model's vector space. */
public interface EmbeddingClient {
  Embedding embed(String text);
}
