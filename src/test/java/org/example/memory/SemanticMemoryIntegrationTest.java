package org.example.memory;

import org.example.memory.semantic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
class SemanticMemoryIntegrationTest {
    @Autowired private SemanticMemoryRepository repository;

    @Test
    void deduplicatesFactsWithinScopeAndForgetsOnlyRequestedRecord() {
        SemanticMemoryIndexer indexer = mock(SemanticMemoryIndexer.class);
        when(indexer.index(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SemanticMemoryService service = new SemanticMemoryService(repository, indexer);
        var first = service.remember("scope-a", "session-1", "Prefer CIFAR");
        var duplicate = service.remember("scope-a", "session-2", " Prefer CIFAR ");
        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1, service.list("scope-a").size());
        var foreign = service.remember("scope-b", "session-3", "Prefer CIFAR");
        assertNotEquals(first.getId(), foreign.getId());
        assertThrows(IllegalArgumentException.class, () -> service.forget("scope-b", first.getId()));
        service.forget("scope-a", first.getId());
        assertTrue(service.list("scope-a").isEmpty());
        assertEquals(1, service.list("scope-b").size());
        verify(indexer).removeVector(first.getId());
        assertEquals(first.getId(), service.remember("scope-a", "session-4", "Prefer CIFAR").getId());
    }
}
