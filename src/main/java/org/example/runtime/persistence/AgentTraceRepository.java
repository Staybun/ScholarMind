package org.example.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, Long> {
    List<AgentTraceEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}

