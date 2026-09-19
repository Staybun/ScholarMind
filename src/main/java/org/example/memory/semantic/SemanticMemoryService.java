package org.example.memory.semantic;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SemanticMemoryService {
    private final SemanticMemoryRepository repository;
    private final SemanticMemoryIndexer indexer;

    public SemanticMemoryService(SemanticMemoryRepository repository, SemanticMemoryIndexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
    }

    public SemanticMemoryEntity remember(String scope, String sessionId, String content) {
        validateScope(scope);
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("Memory content must contain 1-4000 characters");
        }
        String normalized = content.strip();
        String hash = hash(normalized);
        SemanticMemoryEntity memory = repository.findByMemoryScopeAndContentHash(scope, hash).orElseGet(() -> {
            SemanticMemoryEntity created = new SemanticMemoryEntity();
            created.setId(UUID.randomUUID().toString());
            created.setMemoryScope(scope);
            created.setContentHash(hash);
            created.setCreatedAt(Instant.now());
            return created;
        });
        memory.setContent(normalized);
        memory.setSourceSessionId(sessionId);
        memory.setDeleted(false);
        memory.setIndexStatus("PENDING");
        memory.setUpdatedAt(Instant.now());
        return indexer.index(repository.saveAndFlush(memory));
    }

    public List<SemanticMemoryEntity> list(String scope) {
        validateScope(scope);
        return repository.findTop200ByMemoryScopeAndDeletedFalseOrderByCreatedAtDesc(scope);
    }

    public void forget(String scope, String id) {
        SemanticMemoryEntity memory = require(scope, id);
        memory.setDeleted(true);
        memory.setUpdatedAt(Instant.now());
        repository.saveAndFlush(memory);
        indexer.removeVector(id);
    }

    public SemanticMemoryEntity reindex(String scope, String id) { return indexer.index(require(scope, id)); }

    public SemanticMemoryEntity require(String scope, String id) {
        validateScope(scope);
        return repository.findByIdAndMemoryScopeAndDeletedFalse(id, scope)
                .orElseThrow(() -> new IllegalArgumentException("Semantic memory not found in scope"));
    }

    public static void validateScope(String scope) {
        if (scope == null || !scope.matches("[A-Za-z0-9_.:-]{1,128}")) {
            throw new IllegalArgumentException("memoryScope must contain 1-128 ASCII letters, digits, _, ., :, or -");
        }
    }

    private String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
