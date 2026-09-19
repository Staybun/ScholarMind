package org.example.runtime.lifecycle;

import org.example.runtime.model.AgentRunStatus;
import org.example.runtime.persistence.AgentRunEntity;
import org.example.runtime.persistence.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AgentRunManager {

    private final AgentRunRepository repository;
    private final AgentRunStateMachine stateMachine;

    public AgentRunManager(AgentRunRepository repository, AgentRunStateMachine stateMachine) {
        this.repository = repository;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public AgentRunEntity create(org.example.runtime.model.AgentExecutionType type, String requestPayload) {
        Instant now = Instant.now();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(UUID.randomUUID().toString());
        run.setExecutionType(type);
        run.setStatus(AgentRunStatus.PENDING);
        run.setRequestPayload(requestPayload);
        run.setCurrentStep("CREATED");
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return repository.save(run);
    }

    @Transactional
    public AgentRunEntity transition(String runId, AgentRunStatus target, String step) {
        AgentRunEntity run = require(runId);
        stateMachine.validate(run.getStatus(), target);
        run.setStatus(target);
        run.setCurrentStep(step);
        run.setUpdatedAt(Instant.now());
        if (target == AgentRunStatus.COMPLETED || target == AgentRunStatus.FAILED) {
            run.setCompletedAt(Instant.now());
        } else {
            run.setCompletedAt(null);
        }
        return repository.save(run);
    }

    @Transactional
    public AgentRunEntity updateAttempt(String runId, int attempt) {
        AgentRunEntity run = require(runId);
        run.setAttemptCount(attempt);
        run.setErrorMessage(null);
        run.setUpdatedAt(Instant.now());
        return repository.save(run);
    }

    @Transactional
    public AgentRunEntity updateStep(String runId, String step) {
        AgentRunEntity run = require(runId);
        run.setCurrentStep(step);
        run.setUpdatedAt(Instant.now());
        return repository.save(run);
    }

    @Transactional
    public AgentRunEntity complete(String runId, String output) {
        AgentRunEntity run = transition(runId, AgentRunStatus.COMPLETED, "COMPLETED");
        run.setOutput(output);
        run.setErrorMessage(null);
        return repository.save(run);
    }

    @Transactional
    public AgentRunEntity fail(String runId, String errorMessage) {
        AgentRunEntity run = transition(runId, AgentRunStatus.FAILED, "FAILED");
        run.setErrorMessage(errorMessage);
        return repository.save(run);
    }

    public AgentRunEntity require(String runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent Run not found: " + runId));
    }
}
