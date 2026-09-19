package org.example.memory;

import org.example.memory.semantic.*;
import org.example.service.VectorEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SemanticMemoryRetrieverTest {
    @SuppressWarnings("unchecked")
    @Test
    void vectorOutageFallsBackToScopedDatabaseCandidates() {
        SemanticVectorStore vectors = mock(SemanticVectorStore.class);
        ObjectProvider<VectorEmbeddingService> embeddings = mock(ObjectProvider.class);
        when(embeddings.getObject()).thenThrow(new IllegalStateException("offline"));
        SemanticMemoryRepository repository = mock(SemanticMemoryRepository.class);
        SemanticMemoryEntity memory = memory("m1", "Use dataset CIFAR for reproduction");
        when(repository.findTop200ByMemoryScopeAndDeletedFalseOrderByCreatedAtDesc("scope-a"))
                .thenReturn(List.of(memory));
        var result = new SemanticMemoryRetriever(vectors, embeddings, repository, true, .35f)
                .retrieve("scope-a", "CIFAR reproduction", 5);
        assertTrue(result.fallback());
        assertEquals("m1", result.hits().get(0).id());
        assertEquals("DATABASE_LEXICAL", result.hits().get(0).retrievalType());
        verifyNoInteractions(vectors);
    }

    @SuppressWarnings("unchecked")
    @Test
    void staleOrForeignVectorHitsNeverEnterContext() {
        SemanticVectorStore vectors = mock(SemanticVectorStore.class);
        ObjectProvider<VectorEmbeddingService> embeddings = mock(ObjectProvider.class);
        VectorEmbeddingService embedding = mock(VectorEmbeddingService.class);
        when(embeddings.getObject()).thenReturn(embedding);
        when(embedding.generateQueryVector("query")).thenReturn(List.of(1f));
        when(vectors.search("scope-a", List.of(1f), 15)).thenReturn(List.of(
                new SemanticVectorStore.VectorHit("foreign", .99f),
                new SemanticVectorStore.VectorHit("deleted", .98f),
                new SemanticVectorStore.VectorHit("good", .8f)));
        SemanticMemoryRepository repository = mock(SemanticMemoryRepository.class);
        SemanticMemoryEntity good = memory("good", "trusted memory");
        when(repository.findTop200ByMemoryScopeAndDeletedFalseOrderByCreatedAtDesc("scope-a"))
                .thenReturn(List.of(good));
        when(repository.findByIdAndMemoryScopeAndDeletedFalse("good", "scope-a")).thenReturn(Optional.of(good));
        var result = new SemanticMemoryRetriever(vectors, embeddings, repository, true, .35f)
                .retrieve("scope-a", "query", 5);
        assertFalse(result.fallback());
        assertEquals(List.of("good"), result.hits().stream().map(SemanticMemoryRetriever.MemoryHit::id).toList());
    }

    private SemanticMemoryEntity memory(String id, String content) {
        SemanticMemoryEntity memory = new SemanticMemoryEntity();
        memory.setId(id); memory.setContent(content); memory.setMemoryScope("scope-a");
        return memory;
    }
}
