package org.example.memory.semantic;

import org.example.service.VectorEmbeddingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

@Service
public class SemanticMemoryIndexer {
    private static final Logger log = LoggerFactory.getLogger(SemanticMemoryIndexer.class);
    private final SemanticVectorStore vectors;
    private final ObjectProvider<VectorEmbeddingService> embeddings;
    private final SemanticMemoryRepository repository;
    private final boolean enabled;

    public SemanticMemoryIndexer(SemanticVectorStore vectors, ObjectProvider<VectorEmbeddingService> embeddings,
                                   SemanticMemoryRepository repository,
                                   @org.springframework.beans.factory.annotation.Value("${scholarmind.memory.semantic.enabled:true}") boolean enabled) {
        this.vectors = vectors;
        this.embeddings = embeddings;
        this.repository = repository;
        this.enabled = enabled;
    }

    public SemanticMemoryEntity index(SemanticMemoryEntity memory) {
        if (!enabled) {
            memory.setIndexStatus("DISABLED");
            return repository.save(memory);
        }
        try {
            vectors.upsert(memory.getId(), memory.getMemoryScope(), embeddings.getObject().generateEmbedding(memory.getContent()));
            memory.setIndexStatus("INDEXED");
        } catch (Exception e) {
            memory.setIndexStatus("FAILED");
            log.warn("Semantic memory {} retained in database; vector indexing failed: {}", memory.getId(), e.getClass().getSimpleName());
        }
        memory.setUpdatedAt(Instant.now());
        return repository.save(memory);
    }

    public void removeVector(String id) {
        if (!enabled) return;
        try { vectors.delete(id); }
        catch (Exception e) { log.warn("Stale semantic vector {} is excluded by database tombstone", id); }
    }
}
