package org.example.memory.working;

import org.example.context.model.ContextMessage;
import java.util.List;

public record WorkingMemoryItem(String sessionId, String memoryScope, String summary,
                                long summarizedThrough, long lastSequence, long version, List<ContextMessage> messages) {
}
