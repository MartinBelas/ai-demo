package ai.demo.model.rag;

import ai.demo.exception.RagException;
import java.util.List;

public record Embedding(List<Double> values) {
  public Embedding {
    if (values == null
        || values.isEmpty()
        || values.stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
      throw new RagException("Invalid embedding vector");
    }
    values = List.copyOf(values);
    double norm = Math.sqrt(values.stream().mapToDouble(v -> v * v).sum());
    if (!Double.isFinite(norm) || norm == 0) throw new RagException("Invalid embedding norm");
    values = values.stream().map(v -> v / norm).toList();
  }

  public double similarity(Embedding other) {
    if (values.size() != other.values.size()) throw new RagException("Embedding dimensions differ");
    double score = 0;
    for (int i = 0; i < values.size(); i++) score += values.get(i) * other.values.get(i);
    return Math.clamp(score, -1, 1);
  }
}
