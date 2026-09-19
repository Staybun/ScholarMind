package org.example.memory.semantic;

import org.example.service.VectorEmbeddingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SemanticMemoryRetriever {
    private final SemanticVectorStore vectors;
    private final ObjectProvider<VectorEmbeddingService> embeddings;
    private final SemanticMemoryRepository repository;
    private final boolean enabled;
    private final float minScore;

    public SemanticMemoryRetriever(SemanticVectorStore vectors, ObjectProvider<VectorEmbeddingService> embeddings,
            SemanticMemoryRepository repository,
            @Value("${scholarmind.memory.semantic.enabled:true}") boolean enabled,
            @Value("${scholarmind.memory.semantic.min-score:0.35}") float minScore) {
        this.vectors = vectors; this.embeddings = embeddings; this.repository = repository;
        this.enabled = enabled; this.minScore = minScore;
    }

    public Retrieval retrieve(String scope, String query, int topK) {
        SemanticMemoryService.validateScope(scope);
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        int limit = Math.max(1, Math.min(20, topK));
        List<SemanticMemoryEntity> candidates = repository.findTop200ByMemoryScopeAndDeletedFalseOrderByCreatedAtDesc(scope);
        if (candidates.isEmpty()) return new Retrieval(List.of(), false);
        if (enabled) {
            try {
                List<MemoryHit> hits = new ArrayList<>();
                for (var hit : vectors.search(scope, embeddings.getObject().generateQueryVector(query), limit * 3)) {
                    if (hit.score() < minScore) continue;
                    repository.findByIdAndMemoryScopeAndDeletedFalse(hit.id(), scope)
                            .ifPresent(m -> hits.add(new MemoryHit(m.getId(), m.getContent(), m.getSourceSessionId(), hit.score(), "VECTOR")));
                    if (hits.size() >= limit) break;
                }
                if (!hits.isEmpty()) return new Retrieval(hits, false);
                return lexical(candidates, query, limit, true);
            } catch (Exception e) { return lexical(candidates, query, limit, true); }
        }
        return lexical(candidates, query, limit, true);
    }

    private Retrieval lexical(List<SemanticMemoryEntity> memories, String query, int limit, boolean fallback) {
        Set<String> terms = terms(query);
        List<MemoryHit> hits = memories.stream().map(m -> {
            String text = m.getContent().toLowerCase(Locale.ROOT);
            long matched = terms.stream().filter(text::contains).count();
            return new MemoryHit(m.getId(), m.getContent(), m.getSourceSessionId(),
                    (float) matched / Math.max(1, terms.size()), "DATABASE_LEXICAL");
        }).filter(hit -> hit.score() > 0).sorted(Comparator.comparingDouble(MemoryHit::score).reversed())
                .limit(limit).toList();
        return new Retrieval(hits, fallback);
    }

    private Set<String> terms(String query) {
        Set<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(s -> s.length() > 1).collect(Collectors.toSet());
        int[] points = query.codePoints().toArray();
        for (int i = 0; i + 1 < points.length; i++) {
            if (Character.UnicodeScript.of(points[i]) == Character.UnicodeScript.HAN
                    && Character.UnicodeScript.of(points[i + 1]) == Character.UnicodeScript.HAN) {
                terms.add(new String(points, i, 2));
            }
        }
        return terms;
    }

    public record MemoryHit(String id, String content, String sourceSessionId, float score, String retrievalType) { }
    public record Retrieval(List<MemoryHit> hits, boolean fallback) { }
}
