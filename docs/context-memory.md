# ScholarMind Context and Layered Memory

## Scope

Phase 2 connects context and memory to the existing runtime. ReAct (including SSE), Plan-and-Execute and all three research agents use the same context policy. Original conversation messages are stored in a relational database; compaction changes a summary watermark, not the original records.

```text
src/main/java/org/example/
  context/
    ContextManager.java             # unified assembly entry
    ContextManagerConfig.java       # budget and window configuration
    ContextAssembler.java           # bounded, labelled context sections
    SkillContextSelector.java       # select existing project skills
    budget/
      TokenEstimator.java
      TokenBudgetAllocator.java
      ContextBudgetInterceptor.java # per-model-call tool payload protection
    compact/
      SlidingWindowStrategy.java
      ConversationSummaryService.java
      ContextCompactService.java
    model/
      ContextMessage.java
      ContextBundle.java
  memory/
    working/                        # Redis snapshot cache and TTL
    conversation/                   # durable messages and summary watermark
    semantic/                       # durable facts + scoped Milvus vector index
  controller/
    MemoryController.java
    MemoryExceptionHandler.java
```

## Execution

1. Runtime normalizes and persists `sessionId` and `memoryScope` in the Run request.
2. Context Manager loads the durable conversation, using Redis only when its version matches the database.
3. A token-bounded sliding window retains recent complete turns. Older turns are summarized together with the previous summary. A compare-and-set watermark protects against concurrent compaction.
4. Scoped semantic memories, relevant paper chunks and selected Skills are assembled into labelled reference sections. Paper references retain IDs, titles, files, sections and page ranges.
5. The model interceptor bounds tool result payloads while preserving tool call IDs. Truncation is explicitly labelled; an oversized remaining request is rejected rather than silently discarding system instructions.
6. Successful output is checkpointed before saving a user/assistant pair. The pair is unique by `runId` and role. If saving fails, Resume can reuse the completed output checkpoint without another model call.
7. Context usage, memory IDs, paper IDs, Skills and degradation flags are recorded in `CONTEXT_ASSEMBLED` Trace events. Raw assembled prompts are not copied into Trace.

## Memory Policy

- Working Memory: Redis caches the active summary and unsummarized turns, with a default 24-hour TTL. Redis failures fall back to the relational database, with a 30-second retry cooldown.
- Semantic Memory: only explicitly requested facts (`请记住`, `记住:`, `remember:`) or explicit API writes are saved. Model answers are not automatically converted into trusted facts.
- Semantic records are stored in H2 by default or MySQL with the `mysql` profile. Milvus stores their vectors in `scholarmind_semantic_memories`, independently from the paper RAG collection.
- Vector indexing failure leaves the durable record with `FAILED` status. Retrieval falls back to keyword matching over the most recent 200 records in the requested scope. This fallback is not semantic similarity.
- `scholarmind.memory.semantic.enabled=false` disables vector indexing and retrieval, but retains database facts and keyword retrieval (`DISABLED` index status).
- Soft-deleted and foreign-scope records are filtered against the database even when stale vector hits exist. A single-record reindex endpoint is provided.
- Skills remain reusable procedural instructions, not a database of personal facts. Their selected content shares the same input budget.

`sessionId` identifies a conversation. `memoryScope` identifies a namespace for cross-session facts. If omitted, `memoryScope` defaults to `sessionId`, so independent sessions do not share facts accidentally. The browser persists a namespace in localStorage and sends it across newly created sessions. API clients must explicitly reuse their scope.

This project currently has no authenticated user ownership model. **A client-supplied scope is isolation by namespace, not authorization.** Add authentication and server-derived user scopes before exposing these APIs to multiple users.

## API

Runtime requests accept `sessionId` and `memoryScope`; Run responses include both. `/api/chat` and `/api/chat_stream` accept `Id`, `Question`, `MemoryScope`. `/api/research/workflow` accepts `SessionId`, `Task` (or `Question`) and `MemoryScope`.

```json
{
  "executionType": "REACT",
  "input": "Please compare the experimental settings",
  "sessionId": "research-session-2",
  "memoryScope": "researcher-local"
}
```

| Method | Endpoint | Parameters |
| --- | --- | --- |
| POST | `/api/memory/semantic` | JSON: `memoryScope`, `sourceSessionId`, `content` |
| GET | `/api/memory/semantic` | `memoryScope` |
| GET | `/api/memory/semantic/search` | `memoryScope`, `query`, `topK` |
| DELETE | `/api/memory/semantic/{id}` | `memoryScope` |
| POST | `/api/memory/semantic/{id}/reindex` | `memoryScope` |
| GET | `/api/memory/conversations/{sessionId}` | persisted metadata and summary |
| DELETE | `/api/memory/conversations/{sessionId}` | clear this conversation, not semantic facts |

Invalid memory requests return HTTP 400; invalid lifecycle transitions return HTTP 409. Clearing conversation history does not delete long-term facts. Delete facts explicitly using their IDs.

## Configuration and Limits

Defaults in `application.yml`: `max-tokens=16000`, `output-reserve=2000`, `tool-reserve=3000`, `max-history-messages=12`, `summary-tokens=1200`, `rag-top-k=3`. After reserving the base prompt and user input, available input is allocated to summary (15%), recent history (35%), semantic facts (15%), paper/workflow evidence (20%) and Skills (15%). Empty allocations are not yet redistributed.

The token estimator uses a conservative UTF-8-byte heuristic, **not the provider's exact tokenizer**. Budget limits are therefore estimates and must be tuned for the selected model. Model call arguments and available dynamic tool definitions are included in the interceptor estimate. Media tokens are not supported by this text-focused version. Very long non-tool reasoning chains are rejected when their budget is exceeded; they are not resumable at an individual ReAct tool-call boundary.

Model summarization can be disabled with `scholarmind.context.summary-model-enabled=false`. If the summary model is unavailable, bounded, role-labelled excerpts are retained instead, with a Trace degradation flag. Summary compression is lossy; original messages remain durable.

Configure `DASHSCOPE_API_KEY` for real model/embedding calls. Redis uses `SCHOLARMIND_REDIS_HOST`, `SCHOLARMIND_REDIS_PORT`, `SCHOLARMIND_REDIS_PASSWORD`. Milvus connection is lazy, so missing Redis/Milvus does not prevent application startup. Real vector retrieval still requires reachable Milvus and valid embedding credentials. The semantic embedding dimension defaults to 1024 and must match the embedding model and existing collection.

Run `mvn test` for the focused budget, compaction, memory isolation, persistence and runtime recovery tests. Run `mvn spring-boot:run` to serve the application on port 9900; use `-Dspring-boot.run.arguments=--server.port=9901` if needed.
