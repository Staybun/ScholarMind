package org.example.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.example.runtime.model.AgentExecutionType;
import org.example.runtime.model.AgentRunStatus;

import java.time.Instant;

@Entity
@Table(name = "agent_run")
@Getter
@Setter
public class AgentRunEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentExecutionType executionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Lob
    @Column(nullable = false)
    private String requestPayload;

    @Lob
    private String output;

    @Lob
    private String errorMessage;

    @Column(length = 100)
    private String currentStep;

    private int attemptCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    @Version
    private long version;
}

