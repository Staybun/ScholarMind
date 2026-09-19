package org.example.context;

import org.example.context.budget.TokenBudgetAllocator;
import org.example.context.budget.TokenEstimator;
import org.example.context.compact.ConversationSummaryService;
import org.example.context.compact.SlidingWindowStrategy;
import org.example.context.model.ContextMessage;
import org.example.service.AgentModelFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextPolicyTest {
    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void truncatesUnicodeWithoutSplittingSurrogatePairs() {
        String source = "论文\uD83D\uDE00分析".repeat(40);
        String result = estimator.truncate(source, 30);
        assertTrue(estimator.estimate(result) <= 30);
        assertTrue(source.startsWith(result));
        assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)));
        assertEquals("", estimator.truncate(source, 0));
    }

    @Test
    void keepsOnlyCompleteRecentTurns() {
        List<ContextMessage> messages = List.of(new ContextMessage(1, "user", "old"),
                new ContextMessage(2, "assistant", "old answer"),
                new ContextMessage(3, "user", "new"), new ContextMessage(4, "assistant", "new answer"));
        SlidingWindowStrategy.Window window = new SlidingWindowStrategy(estimator).select(messages, 1000, 3);
        assertEquals(List.of(messages.get(2), messages.get(3)), window.retained());
        assertEquals(List.of(messages.get(0), messages.get(1)), window.removed());
        assertTrue(new SlidingWindowStrategy(estimator).select(messages, 1, 12).retained().isEmpty());
    }

    @Test
    void assembledSectionsRespectBudgets() {
        Map<String, Integer> budgets = new TokenBudgetAllocator().allocate(1000);
        Map<String, String> sections = Map.of("history", "历史".repeat(2000),
                "rag", "evidence".repeat(2000), "skills", "skill".repeat(2000));
        ContextAssembler.Assembly result = new ContextAssembler(estimator)
                .assemble("base", "question", sections, budgets, 1500);
        assertTrue(result.estimatedTokens() <= 1500);
        result.usage().forEach((section, tokens) -> assertTrue(tokens <= budgets.get(section)));
        assertThrows(IllegalStateException.class, () -> new ContextAssembler(estimator)
                .assemble("x".repeat(1000), "question", Map.of(), Map.of(), 10));
    }

    @Test
    void summaryFallbackIsBoundedAndDoesNotCallModel() {
        ContextManagerConfig config = new ContextManagerConfig();
        config.setSummaryModelEnabled(false);
        AgentModelFactory model = mock(AgentModelFactory.class);
        ConversationSummaryService.Summary result = new ConversationSummaryService(model, estimator, config)
                .summarize(null, List.of(new ContextMessage(1, "user", "Use dataset ABC"),
                        new ContextMessage(2, "assistant", "Need to verify evidence")), 100);
        assertTrue(result.fallback());
        assertTrue(result.content().contains("user:"));
        assertTrue(estimator.estimate(result.content()) <= 100);
        verifyNoInteractions(model);
    }
}
