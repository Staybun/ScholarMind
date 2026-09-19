package org.example.memory.semantic;

import java.util.List;

public interface SemanticVectorStore {
    void upsert(String id, String scope, List<Float> vector);
    List<VectorHit> search(String scope, List<Float> vector, int topK);
    void delete(String id);
    record VectorHit(String id, float score) { }
}
