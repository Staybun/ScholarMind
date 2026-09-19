package org.example.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "agent_checkpoint")
@Getter
@Setter
public class AgentCheckpointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String runId;

    private int sequenceNumber;

    @Column(nullable = false, length = 100)
    private String stepName;

    @Column(nullable = false, length = 32)
    private String status;

    @Lob
    private String payload;

    private Instant createdAt;
}

