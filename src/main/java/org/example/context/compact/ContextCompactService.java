package org.example.context.compact;

import org.example.memory.conversation.ConversationMemoryService;
import org.example.memory.working.WorkingMemoryItem;
import org.springframework.stereotype.Service;

@Service
public class ContextCompactService {
    private final SlidingWindowStrategy windows;
    private final ConversationSummaryService summaries;
    private final ConversationMemoryService conversations;

    public ContextCompactService(SlidingWindowStrategy windows, ConversationSummaryService summaries,
                                  ConversationMemoryService conversations) {
        this.windows = windows;
        this.summaries = summaries;
        this.conversations = conversations;
    }

    public Compacted compact(WorkingMemoryItem memory, int historyBudget, int summaryBudget, int maxMessages) {
        SlidingWindowStrategy.Window window = windows.select(memory.messages(), historyBudget, maxMessages);
        if (window.removed().isEmpty()) return new Compacted(memory.summary(), window, false, false);
        ConversationSummaryService.Summary summary = summaries.summarize(memory.summary(), window.removed(), summaryBudget);
        long through = window.removed().get(window.removed().size() - 1).sequence();
        boolean saved = conversations.saveSummary(memory.sessionId(), memory.summarizedThrough(), through, summary.content());
        if (!saved) throw new IllegalStateException("Conversation was compacted concurrently; retry context assembly");
        return new Compacted(summary.content(), window, true, summary.fallback());
    }

    public record Compacted(String summary, SlidingWindowStrategy.Window window, boolean compacted, boolean fallback) { }
}
