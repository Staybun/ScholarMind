package org.example.runtime.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AgentRunResult {
    private String runId;
    private String sessionId;
    private String memoryScope;
    private AgentExecutionType executionType;
    private AgentRunStatus status;
    private String output;
    private String errorMessage;
    private String currentStep;
    private int attemptCount;
    private Instant createdAt;
    private Instant updatedAt;
}
