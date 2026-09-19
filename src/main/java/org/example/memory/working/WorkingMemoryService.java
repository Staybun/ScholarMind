package org.example.memory.working;

import java.util.Optional;

public interface WorkingMemoryService {
    Optional<WorkingMemoryItem> get(String sessionId);
    void put(WorkingMemoryItem item);
    void clear(String sessionId);
}
