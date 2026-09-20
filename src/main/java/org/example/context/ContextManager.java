package org.example.context;

import org.example.context.budget.TokenBudgetAllocator;
import org.example.context.budget.TokenEstimator;
import org.example.context.compact.ContextCompactService;
import org.example.context.model.ContextBundle;
import org.example.context.model.ContextMessage;
import org.example.memory.conversation.ConversationMemoryService;
import org.example.memory.semantic.SemanticMemoryRetriever;
import org.example.memory.semantic.SemanticMemoryService;
import org.example.memory.working.WorkingMemoryItem;
import org.example.runtime.model.AgentRunRequest;
import org.example.service.HybridSearchService;
import org.example.service.VectorSearchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ContextManager {
    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);
    private final ContextManagerConfig config;
    private final TokenEstimator estimator;
    private final TokenBudgetAllocator allocator;
    private final ContextAssembler assembler;
    private final ContextCompactService compact;
    private final ConversationMemoryService conversations;
    private final SemanticMemoryRetriever memories;
    private final SemanticMemoryService memoryService;
    private final SkillContextSelector skills;
    private final ObjectProvider<HybridSearchService> rag;
    private final int memoryTopK;
    private final ReentrantLock[] locks = new ReentrantLock[64];

    public ContextManager(ContextManagerConfig config, TokenEstimator estimator, TokenBudgetAllocator allocator,
            ContextAssembler assembler, ContextCompactService compact, ConversationMemoryService conversations,
            SemanticMemoryRetriever memories, SemanticMemoryService memoryService, SkillContextSelector skills,
            ObjectProvider<HybridSearchService> rag,
            @Value("${scholarmind.memory.semantic.top-k:5}") int memoryTopK) {
        this.config = config; this.estimator = estimator; this.allocator = allocator; this.assembler = assembler;
        this.compact = compact; this.conversations = conversations; this.memories = memories;
        this.memoryService = memoryService; this.skills = skills; this.rag = rag; this.memoryTopK = memoryTopK;
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }

    public void normalize(AgentRunRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) request.setSessionId(UUID.randomUUID().toString());
        SemanticMemoryService.validateScope(request.getSessionId());
        if (request.getMemoryScope() == null || request.getMemoryScope().isBlank()) request.setMemoryScope(request.getSessionId());
        SemanticMemoryService.validateScope(request.getMemoryScope());
        if (request.getHistory() == null) request.setHistory(List.of());
    }

    public ContextBundle prepare(AgentRunRequest request, String basePrompt, String supplementalEvidence) {
        normalize(request);
        if (request.getInput() == null || request.getInput().isBlank()) throw new IllegalArgumentException("input is required");
        int inputLimit = config.inputLimit();
        int available = inputLimit - estimator.estimate(basePrompt + ContextAssembler.DATA_RULES)
                - estimator.estimate(request.getInput()) - 400;
        if (available < 128) throw new IllegalArgumentException("User input is too long for the configured context budget");
        Map<String, Integer> budgets = allocator.allocate(available);
        ReentrantLock lock = locks[Math.floorMod(request.getSessionId().hashCode(), locks.length)];
        lock.lock();
        try {
            WorkingMemoryItem memory = conversations.load(request);
            ContextCompactService.Compacted history = compact.compact(memory, budgets.get("history"),
                    Math.min(config.getSummaryTokens(), budgets.get("summary")), config.getMaxHistoryMessages());
            List<String> warnings = new ArrayList<>();
            if (history.fallback()) warnings.add("SUMMARY_EXTRACTIVE_FALLBACK");
            SemanticMemoryRetriever.Retrieval retrieved = memories.retrieve(request.getMemoryScope(), request.getInput(), memoryTopK);
            if (retrieved.fallback()) warnings.add("SEMANTIC_DATABASE_FALLBACK");
            List<SkillContextSelector.SelectedSkill> selected;
            try { selected = skills.select(request.getInput()); }
            catch (Exception e) { selected = List.of(); warnings.add("SKILL_UNAVAILABLE"); }
            List<VectorSearchService.SearchResult> evidence = List.of();
            if (shouldRetrievePapers(request.getInput())) {
                try { evidence = rag.getObject().search(request.getInput(), Math.max(1, Math.min(10, config.getRagTopK()))); }
                catch (Exception e) { warnings.add("RAG_UNAVAILABLE_USE_TOOL_OR_REPORT_INSUFFICIENT_EVIDENCE"); }
            }
            Map<String, String> sections = new LinkedHashMap<>();
            sections.put("summary", history.summary());
            sections.put("history", history.window().retained().stream().map(ContextMessage::render).reduce("", String::concat));
            sections.put("semantic", retrieved.hits().stream().map(hit -> "[memory:" + hit.id() + ", source-session:"
                    + hit.sourceSessionId() + ", " + hit.retrievalType() + "] " + hit.content() + "\n").reduce("", String::concat));
            StringBuilder paperEvidence = new StringBuilder();
            if (supplementalEvidence != null && !supplementalEvidence.isBlank()) {
                paperEvidence.append("Previous workflow results (retain their source citations):\n").append(supplementalEvidence).append('\n');
            }
            for (var hit : evidence) {
                paperEvidence.append("[paper:").append(hit.getId()).append(", title:").append(hit.getDocumentTitle())
                        .append(", file:").append(hit.getSourceFileName()).append(", section:").append(hit.getSectionTitle())
                        .append(", pages:").append(hit.getPageStart()).append('-').append(hit.getPageEnd())
                        .append("]\n").append(hit.getContent()).append('\n');
            }
            sections.put("rag", paperEvidence.toString());
            sections.put("skills", selected.stream().map(s -> "Skill: " + s.name() + "\n" + s.markdown() + "\n").reduce("", String::concat));
            ContextAssembler.Assembly assembly = assembler.assemble(basePrompt, request.getInput(), sections, budgets, inputLimit);
            return new ContextBundle(assembly.prompt(), request.getInput(), assembly.estimatedTokens(), inputLimit,
                    history.compacted(), history.window().retained().size(), selected.stream().map(SkillContextSelector.SelectedSkill::name).toList(),
                    retrieved.hits().stream().map(SemanticMemoryRetriever.MemoryHit::id).toList(),
                    evidence.stream().map(VectorSearchService.SearchResult::getId).toList(), warnings, assembly.usage());
        } finally { lock.unlock(); }
    }

    public void complete(AgentRunRequest request, String runId, String output) {
        normalize(request);
        conversations.load(request);
        conversations.append(request, runId, output);
        String input = request.getInput();
        var command = java.util.regex.Pattern.compile("(?is)^\\s*(?:请记住|记住\\s*[:： ]|remember\\s*[: ])\\s*(.+)$")
                .matcher(input == null ? "" : input);
        if (command.matches()) {
            try { memoryService.remember(request.getMemoryScope(), request.getSessionId(), estimator.truncate(command.group(1), 1500)); }
            catch (Exception e) { log.warn("Explicit memory capture failed; original conversation is retained: {}", e.getClass().getSimpleName()); }
        }
    }

    private boolean shouldRetrievePapers(String input) {
        return input.toLowerCase(Locale.ROOT).matches("(?s).*(论文|文献|复现|数据集|消融|paper|research|experiment|ablation).*" );
    }
}
