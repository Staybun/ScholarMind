package org.example.memory.semantic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "semantic_memory", uniqueConstraints = @UniqueConstraint(columnNames = {"memory_scope", "content_hash"}),
        indexes = @Index(columnList = "memory_scope,deleted"))
@Getter
@Setter
public class SemanticMemoryEntity {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "memory_scope", nullable = false, length = 128)
    private String memoryScope;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Lob
    private String content;
    @Column(length = 128)
    private String sourceSessionId;
    @Column(length = 32)
    private String indexStatus;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
