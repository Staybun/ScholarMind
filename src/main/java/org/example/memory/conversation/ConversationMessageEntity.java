package org.example.memory.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "memory_message", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"run_id", "role"}),
        @UniqueConstraint(columnNames = {"session_id", "sequence_number"})
}, indexes = @Index(columnList = "session_id,sequence_number"))
@Getter
@Setter
public class ConversationMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;
    @Column(name = "run_id", nullable = false, length = 128)
    private String runId;
    @Column(nullable = false, length = 16)
    private String role;
    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;
    @Lob
    private String content;
}
