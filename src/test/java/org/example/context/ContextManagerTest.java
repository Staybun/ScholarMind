package org.example.context;

import org.example.context.budget.*;
import org.example.context.compact.*;
import org.example.context.model.*;
import org.example.memory.conversation.ConversationMemoryService;
import org.example.memory.semantic.*;
import org.example.memory.working.WorkingMemoryItem;
import org.example.runtime.model.AgentRunRequest;
import org.example.service.AgentModelFactory;
import org.example.service.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextManagerTest {
    @Test
    void capturesOnlyExplicitMemoryCommandsNotQuotedOrNegatedRequests() {
        ConversationMemoryService conversations = mock(ConversationMemoryService.class);
        SemanticMemoryService facts = mock(SemanticMemoryService.class);
        ContextManager manager = new ContextManager(new ContextManagerConfig(), new TokenEstimator(),
                null, null, null, conversations, null, facts, null, null, 5);
        AgentRunRequest request = new AgentRunRequest();
        request.setSessionId("session-a"); request.setMemoryScope("scope-a");
        request.setInput("不要记住: dataset ABC");
        manager.complete(request, "run-a", "answer");
        verifyNoInteractions(facts);
        request.setInput("请记住 dataset ABC");
        manager.complete(request, "run-b", "answer");
        verify(facts).remember("scope-a", "session-a", "dataset ABC");
    }
    @SuppressWarnings("unchecked")
    @Test
    void assemblesScopedMemoriesSkillsAndCompactedHistoryUnderBudget() {
        ContextManagerConfig config = new ContextManagerConfig();
        config.setMaxTokens(6000); config.setOutputReserve(1000); config.setToolReserve(1000);
        config.setMaxHistoryMessages(4); config.setSummaryModelEnabled(false);
        TokenEstimator estimator = new TokenEstimator();
        ConversationMemoryService conversations = mock(ConversationMemoryService.class);
        List<ContextMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 20; i++) messages.add(new ContextMessage(i,
                i % 2 == 1 ? "user" : "assistant", "history entry " + i));
        when(conversations.load(any())).thenReturn(new WorkingMemoryItem("session-a", "scope-a", null, 0, 20, 1, messages));
        when(conversations.saveSummary(anyString(), anyLong(), anyLong(), anyString())).thenReturn(true);
        ContextCompactService compact = new ContextCompactService(new SlidingWindowStrategy(estimator),
                new ConversationSummaryService(mock(AgentModelFactory.class), estimator, config), conversations);
        SemanticMemoryRetriever retriever = mock(SemanticMemoryRetriever.class);
        when(retriever.retrieve("scope-a", "请复现论文", 5)).thenReturn(new SemanticMemoryRetriever.Retrieval(List.of(
                new SemanticMemoryRetriever.MemoryHit("m1", "Prefer CIFAR dataset", "prior-session", .8f, "VECTOR")), false));
        SkillContextSelector skills = mock(SkillContextSelector.class);
        when(skills.select(anyString())).thenReturn(List.of(new SkillContextSelector.SelectedSkill("experiment-reproduction", "Verify data and metrics")));
        ObjectProvider<HybridSearchService> rag = mock(ObjectProvider.class);
        when(rag.getObject()).thenThrow(new IllegalStateException("offline"));
        ContextManager manager = new ContextManager(config, estimator, new TokenBudgetAllocator(), new ContextAssembler(estimator),
                compact, conversations, retriever, mock(SemanticMemoryService.class), skills, rag, 5);
        AgentRunRequest request = new AgentRunRequest();
        request.setSessionId("session-a"); request.setMemoryScope("scope-a"); request.setInput("请复现论文");
        ContextBundle result = manager.prepare(request, "Follow evidence", "[paper:1] previous workflow evidence");
        assertTrue(result.compacted());
        assertEquals(4, result.historyMessageCount());
        assertTrue(result.estimatedTokens() <= config.inputLimit());
        assertEquals(List.of("m1"), result.memoryIds());
        assertEquals(List.of("experiment-reproduction"), result.selectedSkills());
        assertTrue(result.systemPrompt().contains("source-session:prior-session"));
        assertTrue(result.systemPrompt().contains("[paper:1]"));
        assertTrue(result.warnings().contains("SUMMARY_EXTRACTIVE_FALLBACK"));
        assertTrue(result.warnings().contains("RAG_UNAVAILABLE_USE_TOOL_OR_REPORT_INSUFFICIENT_EVIDENCE"));
        verify(conversations).saveSummary(eq("session-a"), eq(0L), eq(16L), anyString());
    }
}
