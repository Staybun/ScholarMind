package org.example.memory.conversation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.sessionId = :id")
    Optional<ConversationEntity> findLocked(@Param("id") String sessionId);
}
