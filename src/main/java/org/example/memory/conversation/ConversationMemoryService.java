package org.example.memory.conversation;

import org.example.context.model.ContextMessage;
import org.example.memory.working.WorkingMemoryItem;
import org.example.memory.working.WorkingMemoryService;
import org.example.runtime.model.AgentRunRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ConversationMemoryService {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final WorkingMemoryService working;

    public ConversationMemoryService(ConversationRepository conversations,
                                      ConversationMessageRepository messages, WorkingMemoryService working) {
        this.conversations = conversations;
        this.messages = messages;
        this.working = working;
    }

    @Transactional
    public WorkingMemoryItem load(AgentRunRequest request) {
        ConversationEntity conversation = conversations.findById(request.getSessionId()).orElseGet(() -> {
            ConversationEntity created = new ConversationEntity();
            created.setSessionId(request.getSessionId());
            created.setMemoryScope(request.getMemoryScope());
            created.setUpdatedAt(Instant.now());
            return conversations.saveAndFlush(created);
        });
        if (!conversation.getMemoryScope().equals(request.getMemoryScope())) {
            throw new IllegalArgumentException("Session belongs to a different memoryScope");
        }
        if (conversation.getLastSequence() == 0 && request.getHistory() != null && !request.getHistory().isEmpty()) {
            List<Map<String, String>> history = request.getHistory();
            int start = Math.max(0, history.size() - 200);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> entry = history.get(i);
                String role = entry.get("role");
                String content = entry.get("content");
                if (("user".equals(role) || "assistant".equals(role)) && content != null) {
                    insert(conversation, "bootstrap:" + request.getSessionId() + ":" + i, role, content);
                }
            }
            conversations.saveAndFlush(conversation);
        }
        WorkingMemoryItem cached = working.get(request.getSessionId()).orElse(null);
        if (cached != null && cached.memoryScope().equals(conversation.getMemoryScope())
                && cached.lastSequence() == conversation.getLastSequence()
                && cached.version() == conversation.getVersion()
                && cached.summarizedThrough() == conversation.getSummarizedThrough()) return cached;
        return snapshot(conversation);
    }

    @Transactional
    public void append(AgentRunRequest request, String runId, String output) {
        ConversationEntity conversation = conversations.findLocked(request.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Conversation was not initialized"));
        if (!conversation.getMemoryScope().equals(request.getMemoryScope())) {
            throw new IllegalArgumentException("Session belongs to a different memoryScope");
        }
        if (messages.existsByRunId(runId)) return;
        insert(conversation, runId, "user", request.getInput());
        insert(conversation, runId, "assistant", output);
        conversation.setUpdatedAt(Instant.now());
        conversations.saveAndFlush(conversation);
        working.clear(request.getSessionId());
        working.put(snapshot(conversation));
    }

    @Transactional
    public boolean saveSummary(String sessionId, long expectedThrough, long through, String summary) {
        ConversationEntity conversation = conversations.findLocked(sessionId).orElseThrow();
        if (conversation.getSummarizedThrough() != expectedThrough) return false;
        if (through > conversation.getLastSequence() || through < expectedThrough) {
            throw new IllegalArgumentException("Invalid summary watermark");
        }
        conversation.setSummary(summary);
        conversation.setSummarizedThrough(through);
        conversation.setUpdatedAt(Instant.now());
        conversations.saveAndFlush(conversation);
        working.clear(sessionId);
        return true;
    }

    @Transactional
    public void clear(String sessionId) {
        ConversationEntity conversation = conversations.findLocked(sessionId).orElse(null);
        if (conversation != null) {
            messages.deleteBySessionId(sessionId);
            conversation.setSummary(null);
            conversation.setSummarizedThrough(0);
            conversation.setLastSequence(0);
            conversation.setUpdatedAt(Instant.now());
            conversations.saveAndFlush(conversation);
        }
        working.clear(sessionId);
    }

    public ConversationEntity get(String sessionId) {
        return conversations.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
    }

    private void insert(ConversationEntity conversation, String runId, String role, String content) {
        ConversationMessageEntity entry = new ConversationMessageEntity();
        entry.setSessionId(conversation.getSessionId());
        entry.setRunId(runId);
        entry.setRole(role);
        entry.setContent(content);
        entry.setSequenceNumber(conversation.getLastSequence() + 1);
        conversation.setLastSequence(entry.getSequenceNumber());
        messages.save(entry);
    }

    private WorkingMemoryItem snapshot(ConversationEntity conversation) {
        List<ContextMessage> entries = messages
                .findBySessionIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                        conversation.getSessionId(), conversation.getSummarizedThrough())
                .stream().map(m -> new ContextMessage(m.getSequenceNumber(), m.getRole(), m.getContent())).toList();
        WorkingMemoryItem snapshot = new WorkingMemoryItem(conversation.getSessionId(),
                conversation.getMemoryScope(), conversation.getSummary(), conversation.getSummarizedThrough(),
                conversation.getLastSequence(), conversation.getVersion(), entries);
        if (entries.size() <= 200) working.put(snapshot);
        return snapshot;
    }
}
