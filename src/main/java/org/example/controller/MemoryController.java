package org.example.controller;

import org.example.memory.conversation.ConversationMemoryService;
import org.example.memory.conversation.ConversationEntity;
import org.example.memory.semantic.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private final SemanticMemoryService memories;
    private final SemanticMemoryRetriever retriever;
    private final ConversationMemoryService conversations;

    public MemoryController(SemanticMemoryService memories, SemanticMemoryRetriever retriever,
                              ConversationMemoryService conversations) {
        this.memories = memories; this.retriever = retriever; this.conversations = conversations;
    }

    @PostMapping("/semantic")
    public SemanticMemoryEntity remember(@RequestBody RememberRequest request) {
        return memories.remember(request.memoryScope(), request.sourceSessionId(), request.content());
    }
    @GetMapping("/semantic")
    public List<SemanticMemoryEntity> list(@RequestParam String memoryScope) { return memories.list(memoryScope); }
    @GetMapping("/semantic/search")
    public SemanticMemoryRetriever.Retrieval search(@RequestParam String memoryScope, @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) { return retriever.retrieve(memoryScope, query, topK); }
    @DeleteMapping("/semantic/{id}")
    public void forget(@PathVariable String id, @RequestParam String memoryScope) { memories.forget(memoryScope, id); }
    @PostMapping("/semantic/{id}/reindex")
    public SemanticMemoryEntity reindex(@PathVariable String id, @RequestParam String memoryScope) { return memories.reindex(memoryScope, id); }
    @GetMapping("/conversations/{sessionId}")
    public ConversationEntity conversation(@PathVariable String sessionId) { return conversations.get(sessionId); }
    @DeleteMapping("/conversations/{sessionId}")
    public void clear(@PathVariable String sessionId) { conversations.clear(sessionId); }
    public record RememberRequest(String memoryScope, String sourceSessionId, String content) { }
}
