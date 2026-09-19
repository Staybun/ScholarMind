package org.example.memory.semantic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SemanticMemoryRepository extends JpaRepository<SemanticMemoryEntity, String> {
    Optional<SemanticMemoryEntity> findByMemoryScopeAndContentHash(String scope, String hash);
    List<SemanticMemoryEntity> findTop200ByMemoryScopeAndDeletedFalseOrderByCreatedAtDesc(String scope);
    Optional<SemanticMemoryEntity> findByIdAndMemoryScopeAndDeletedFalse(String id, String scope);
}
