package org.example.memory;

import org.example.memory.conversation.*;
import org.example.memory.working.WorkingMemoryService;
import org.example.runtime.model.AgentRunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
class ConversationMemoryIntegrationTest {
    @Autowired private ConversationRepository conversations;
    @Autowired private ConversationMessageRepository messages;
    private ConversationMemoryService service;
    private WorkingMemoryService working;

    @BeforeEach
    void setup() {
        working = mock(WorkingMemoryService.class);
        when(working.get(anyString())).thenReturn(Optional.empty());
        service = new ConversationMemoryService(conversations, messages, working);
    }

    private AgentRunRequest request(String session, String scope) {
        AgentRunRequest request = new AgentRunRequest();
        request.setSessionId(session); request.setMemoryScope(scope);
        request.setInput("question"); request.setHistory(List.of());
        return request;
    }

    @Test
    void completedRunIsSavedOnceAndScopeCannotBeChanged() {
        AgentRunRequest request = request("session-a", "user-a");
        service.load(request);
        service.append(request, "run-a", "answer");
        service.append(request, "run-a", "duplicate");
        assertEquals(2, messages.countBySessionId("session-a"));
        assertEquals(2, service.load(request).lastSequence());
        assertThrows(IllegalArgumentException.class, () -> service.load(request("session-a", "user-b")));
        assertEquals(0, service.load(request("session-b", "user-a")).messages().size());
    }

    @Test
    void summaryWatermarkSurvivesReloadWithoutDeletingOriginalMessages() {
        AgentRunRequest request = request("session-a", "user-a");
        service.load(request);
        service.append(request, "run-a", "answer");
        service.append(request, "run-b", "second answer");
        assertTrue(service.saveSummary("session-a", 0, 2, "summary"));
        assertFalse(service.saveSummary("session-a", 0, 4, "stale summary"));
        assertEquals("summary", service.load(request).summary());
        assertEquals(2, service.load(request).messages().size());
        assertEquals(4, messages.countBySessionId("session-a"));
        service.clear("session-a");
        assertTrue(service.load(request).messages().isEmpty());
        assertNull(service.load(request).summary());
        verify(working, atLeastOnce()).clear("session-a");
    }
}
