package org.example.runtime.trace;

import org.example.runtime.persistence.AgentTraceEntity;
import org.example.runtime.persistence.AgentTraceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AgentTraceRecorder {

    private final AgentTraceRepository repository;

    public AgentTraceRecorder(AgentTraceRepository repository) {
        this.repository = repository;
    }

    public void record(String runId, String eventType, String stepName, Integer attempt,
                       Long durationMs, String detail) {
        AgentTraceEntity trace = new AgentTraceEntity();
        trace.setRunId(runId);
        trace.setEventType(eventType);
        trace.setStepName(stepName);
        trace.setAttempt(attempt);
        trace.setDurationMs(durationMs);
        trace.setDetail(detail);
        trace.setCreatedAt(Instant.now());
        repository.save(trace);
    }

    public List<AgentTraceEntity> list(String runId) {
        return repository.findByRunIdOrderByCreatedAtAsc(runId);
    }
}

