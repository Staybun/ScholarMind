package org.example.memory.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, Long> {
    List<ConversationMessageEntity> findBySessionIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(String id, long sequence);
    boolean existsByRunId(String runId);
    long countBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}
