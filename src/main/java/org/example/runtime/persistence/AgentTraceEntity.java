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
@Table(name = "agent_trace")
@Getter
@Setter
public class AgentTraceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String runId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 100)
    private String stepName;

    private Integer attempt;
    private Long durationMs;

    @Lob
    private String detail;

    private Instant createdAt;
}

