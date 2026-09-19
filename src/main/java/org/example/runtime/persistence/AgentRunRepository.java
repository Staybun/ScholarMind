package org.example.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {
}

