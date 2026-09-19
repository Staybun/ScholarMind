package org.example.runtime.checkpoint;

import org.example.runtime.persistence.AgentCheckpointEntity;
import org.example.runtime.persistence.AgentCheckpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CheckpointService {

    private final AgentCheckpointRepository repository;

    public CheckpointService(AgentCheckpointRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AgentCheckpointEntity save(String runId, String stepName, String status, String payload) {
        AgentCheckpointEntity checkpoint = new AgentCheckpointEntity();
        checkpoint.setRunId(runId);
        checkpoint.setSequenceNumber((int) repository.countByRunId(runId) + 1);
        checkpoint.setStepName(stepName);
        checkpoint.setStatus(status);
        checkpoint.setPayload(payload);
        checkpoint.setCreatedAt(Instant.now());
        return repository.save(checkpoint);
    }

    public Optional<AgentCheckpointEntity> latest(String runId) {
        return repository.findFirstByRunIdOrderBySequenceNumberDesc(runId);
    }

    public Optional<AgentCheckpointEntity> latestCompleted(String runId) {
        return repository.findFirstByRunIdAndStatusOrderBySequenceNumberDesc(runId, "COMPLETED");
    }

    public List<AgentCheckpointEntity> list(String runId) {
        return repository.findByRunIdOrderBySequenceNumberAsc(runId);
    }
}
