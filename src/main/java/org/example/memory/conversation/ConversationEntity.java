package org.example.memory.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "memory_conversation")
@Getter
@Setter
public class ConversationEntity {
    @Id
    @Column(length = 128)
    private String sessionId;
    @Column(nullable = false, length = 128)
    private String memoryScope;
    @Lob
    private String summary;
    private long summarizedThrough;
    private long lastSequence;
    private Instant updatedAt;
    @Version
    private long version;
}
