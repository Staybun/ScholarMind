package org.example.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentCheckpointRepository extends JpaRepository<AgentCheckpointEntity, Long> {
    List<AgentCheckpointEntity> findByRunIdOrderBySequenceNumberAsc(String runId);

    Optional<AgentCheckpointEntity> findFirstByRunIdOrderBySequenceNumberDesc(String runId);

    Optional<AgentCheckpointEntity> findFirstByRunIdAndStatusOrderBySequenceNumberDesc(String runId, String status);

    long countByRunId(String runId);
}
