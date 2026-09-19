package org.example.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.runtime.checkpoint.CheckpointService;
import org.example.runtime.execution.AgentExecutionContext;
import org.example.runtime.execution.AgentExecutionResult;
import org.example.runtime.execution.AgentExecutor;
import org.example.runtime.execution.AgentStepListener;
import org.example.runtime.lifecycle.AgentRunManager;
import org.example.runtime.model.AgentExecutionType;
import org.example.runtime.model.AgentRunRequest;
import org.example.runtime.model.AgentRunResult;
import org.example.runtime.model.AgentRunStatus;
import org.example.runtime.persistence.AgentCheckpointEntity;
import org.example.runtime.persistence.AgentRunEntity;
import org.example.runtime.retry.RetryExecutor;
import org.example.runtime.trace.AgentTraceRecorder;
import org.springframework.stereotype.Service;
import org.example.context.ContextManager;
import org.example.context.model.ContextBundle;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AgentRuntime {

    private static final String EXECUTION_STEP = "AGENT_EXECUTION";

    private final AgentRunManager runManager;
    private final CheckpointService checkpointService;
    private final AgentTraceRecorder traceRecorder;
    private final RetryExecutor retryExecutor;
    private final ObjectMapper objectMapper;
    private final Map<AgentExecutionType, AgentExecutor> executors;
    private final ContextManager contexts;
    private final ReentrantLock[] runLocks = createRunLocks(64);

    public AgentRuntime(AgentRunManager runManager,
                        CheckpointService checkpointService,
                        AgentTraceRecorder traceRecorder,
                        RetryExecutor retryExecutor,
                        ObjectMapper objectMapper,
                        List<AgentExecutor> executorList,
                        ContextManager contexts) {
        this.runManager = runManager;
        this.checkpointService = checkpointService;
        this.traceRecorder = traceRecorder;
        this.retryExecutor = retryExecutor;
        this.objectMapper = objectMapper;
        this.contexts = contexts;
        this.executors = new EnumMap<>(AgentExecutionType.class);
        executorList.forEach(executor -> this.executors.put(executor.executionType(), executor));
    }

    public AgentRunResult start(AgentRunRequest request) {
        validate(request);
        AgentRunEntity run = runManager.create(request.getExecutionType(), serialize(request));
        checkpointService.save(run.getId(), "RUN_INPUT", "COMPLETED", run.getRequestPayload());
        traceRecorder.record(run.getId(), "RUN_CREATED", "CREATED", null, null,
                "Execution type: " + request.getExecutionType());
        return execute(run, request, null);
    }

    public AgentRunResult resume(String runId) {
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            AgentRunEntity run = runManager.require(runId);
            if (run.getStatus() == AgentRunStatus.COMPLETED) {
                return toResult(run);
            }
            if (run.getStatus() != AgentRunStatus.FAILED) {
                throw new IllegalStateException("Only FAILED Agent Runs can be resumed. Current status: " + run.getStatus());
            }

            AgentRunRequest request = deserialize(run.getRequestPayload());
            String checkpointPayload = checkpointService.latestCompleted(runId)
                    .map(AgentCheckpointEntity::getPayload)
                    .orElse(null);
            traceRecorder.record(runId, "RUN_RESUMED", run.getCurrentStep(), null, null,
                    "Resume from latest completed checkpoint");
            return execute(run, request, checkpointPayload);
        } finally {
            lock.unlock();
        }
    }

    public AgentRunResult get(String runId) {
        return toResult(runManager.require(runId));
    }

    public AgentRunResult beginStreaming(AgentRunRequest request) {
        validate(request);
        AgentRunEntity run = runManager.create(request.getExecutionType(), serialize(request));
        checkpointService.save(run.getId(), "RUN_INPUT", "COMPLETED", run.getRequestPayload());
        traceRecorder.record(run.getId(), "RUN_CREATED", "CREATED", null, null,
                "Streaming execution type: " + request.getExecutionType());
        runManager.transition(run.getId(), AgentRunStatus.RUNNING, EXECUTION_STEP);
        runManager.updateAttempt(run.getId(), 1);
        traceRecorder.record(run.getId(), "RUN_STARTED", EXECUTION_STEP, null, null, "SSE streaming run");
        traceRecorder.record(run.getId(), "ATTEMPT_STARTED", EXECUTION_STEP, 1, null, null);
        return get(run.getId());
    }

    public void retryStreaming(String runId, Throwable failure) {
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            AgentRunEntity run = runManager.require(runId);
            traceRecorder.record(runId, "ATTEMPT_FAILED", EXECUTION_STEP, run.getAttemptCount(), null,
                    throwableMessage(failure));
            runManager.transition(runId, AgentRunStatus.RETRYING, EXECUTION_STEP);
            traceRecorder.record(runId, "RETRY_SCHEDULED", EXECUTION_STEP, run.getAttemptCount(), null, null);
            runManager.transition(runId, AgentRunStatus.RUNNING, EXECUTION_STEP);
            int nextAttempt = run.getAttemptCount() + 1;
            runManager.updateAttempt(runId, nextAttempt);
            traceRecorder.record(runId, "ATTEMPT_STARTED", EXECUTION_STEP, nextAttempt, null, null);
        } finally {
            lock.unlock();
        }
    }

    public AgentRunResult completeStreaming(String runId, String output) {
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            checkpointService.save(runId, EXECUTION_STEP, "COMPLETED", output);
            contexts.complete(deserialize(runManager.require(runId).getRequestPayload()), runId, output);
            AgentRunEntity completed = runManager.complete(runId, output);
            traceRecorder.record(runId, "RUN_COMPLETED", EXECUTION_STEP, completed.getAttemptCount(),
                    Duration.between(completed.getCreatedAt(), Instant.now()).toMillis(), "SSE streaming run");
            return toResult(completed);
        } finally {
            lock.unlock();
        }
    }

    public AgentRunResult failStreaming(String runId, Throwable failure) {
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            AgentRunEntity run = runManager.require(runId);
            String message = throwableMessage(failure);
            traceRecorder.record(runId, "ATTEMPT_FAILED", EXECUTION_STEP, run.getAttemptCount(), null, message);
            checkpointService.save(runId, EXECUTION_STEP, "FAILED", message);
            AgentRunEntity failed = runManager.fail(runId, message);
            traceRecorder.record(runId, "RUN_FAILED", EXECUTION_STEP, failed.getAttemptCount(),
                    Duration.between(failed.getCreatedAt(), Instant.now()).toMillis(), message);
            return toResult(failed);
        } finally {
            lock.unlock();
        }
    }

    public int maxAttempts() {
        return retryExecutor.getMaxAttempts();
    }

    public void recordContext(String runId, ContextBundle context) {
        try {
            String detail = objectMapper.writeValueAsString(Map.of(
                    "estimatedTokens", context.estimatedTokens(), "inputLimit", context.inputLimit(),
                    "compacted", context.compacted(), "historyMessages", context.historyMessageCount(),
                    "skills", context.selectedSkills(), "memoryIds", context.memoryIds(),
                    "evidenceIds", context.evidenceIds(), "warnings", context.warnings(),
                    "sectionTokens", context.sectionTokens()));
            traceRecorder.record(runId, "CONTEXT_ASSEMBLED", runManager.require(runId).getCurrentStep(), null, null, detail);
        } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to record context statistics", e); }
    }

    private AgentRunResult execute(AgentRunEntity run, AgentRunRequest request, String checkpointPayload) {
        AgentExecutor executor = executors.get(request.getExecutionType());
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for " + request.getExecutionType());
        }

        runManager.transition(run.getId(), AgentRunStatus.RUNNING, EXECUTION_STEP);
        Instant startedAt = Instant.now();
        int previousAttempts = run.getAttemptCount();
        traceRecorder.record(run.getId(), "RUN_STARTED", EXECUTION_STEP, null, null, null);
        try {
            AgentExecutionResult executionResult = retryExecutor.execute(
                    () -> {
                        AgentCheckpointEntity latest = checkpointService.latestCompleted(run.getId()).orElse(null);
                        String latestStep = latest == null ? null : latest.getStepName();
                        String latestPayload = latest == null ? checkpointPayload : latest.getPayload();
                        if (EXECUTION_STEP.equals(latestStep)) return AgentExecutionResult.completed(latestPayload);
                        return executor.execute(new AgentExecutionContext(run.getId(), request,
                                latestStep, latestPayload, stepListener(run.getId())));
                    },
                    attempt -> beforeAttempt(run.getId(), previousAttempts + attempt),
                    (attempt, exception) -> afterFailure(run.getId(), previousAttempts + attempt, exception)
            );
            checkpointService.save(run.getId(), EXECUTION_STEP, "COMPLETED", executionResult.checkpointPayload());
            contexts.complete(request, run.getId(), executionResult.output());
            AgentRunEntity completed = runManager.complete(run.getId(), executionResult.output());
            traceRecorder.record(run.getId(), "RUN_COMPLETED", EXECUTION_STEP, completed.getAttemptCount(),
                    Duration.between(startedAt, Instant.now()).toMillis(), null);
            return toResult(completed);
        } catch (Exception exception) {
            String message = rootMessage(exception);
            checkpointService.save(run.getId(), EXECUTION_STEP, "FAILED", message);
            AgentRunEntity failed = runManager.fail(run.getId(), message);
            traceRecorder.record(run.getId(), "RUN_FAILED", EXECUTION_STEP, failed.getAttemptCount(),
                    Duration.between(startedAt, Instant.now()).toMillis(), message);
            return toResult(failed);
        }
    }

    private void beforeAttempt(String runId, int attempt) {
        AgentRunEntity run = runManager.require(runId);
        if (run.getStatus() == AgentRunStatus.RETRYING) {
            runManager.transition(runId, AgentRunStatus.RUNNING, EXECUTION_STEP);
        }
        runManager.updateAttempt(runId, attempt);
        traceRecorder.record(runId, "ATTEMPT_STARTED", EXECUTION_STEP, attempt, null, null);
    }

    private void afterFailure(String runId, int attempt, Exception exception) {
        runManager.transition(runId, AgentRunStatus.RETRYING, EXECUTION_STEP);
        traceRecorder.record(runId, "ATTEMPT_FAILED", EXECUTION_STEP, attempt, null, rootMessage(exception));
    }

    private AgentStepListener stepListener(String runId) {
        return new AgentStepListener() {
            @Override
            public void contextAssembled(ContextBundle context) { recordContext(runId, context); }
            @Override
            public void started(String stepName) {
                runManager.updateStep(runId, stepName);
                traceRecorder.record(runId, "STEP_STARTED", stepName,
                        runManager.require(runId).getAttemptCount(), null, null);
            }

            @Override
            public void completed(String stepName, String payload) {
                checkpointService.save(runId, stepName, "COMPLETED", payload);
                traceRecorder.record(runId, "STEP_COMPLETED", stepName,
                        runManager.require(runId).getAttemptCount(), null, null);
            }
        };
    }

    private void validate(AgentRunRequest request) {
        if (request == null || request.getExecutionType() == null) {
            throw new IllegalArgumentException("executionType is required");
        }
        if (request.getInput() == null || request.getInput().isBlank()) {
            throw new IllegalArgumentException("input is required");
        }
        contexts.normalize(request);
    }

    private String serialize(AgentRunRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize Agent Run request", exception);
        }
    }

    private AgentRunRequest deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AgentRunRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to restore Agent Run request", exception);
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String throwableMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private ReentrantLock lockFor(String runId) {
        return runLocks[Math.floorMod(runId.hashCode(), runLocks.length)];
    }

    private static ReentrantLock[] createRunLocks(int size) {
        ReentrantLock[] locks = new ReentrantLock[size];
        for (int i = 0; i < size; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private AgentRunResult toResult(AgentRunEntity run) {
        AgentRunRequest request = deserialize(run.getRequestPayload());
        return AgentRunResult.builder()
                .runId(run.getId())
                .sessionId(request.getSessionId())
                .memoryScope(request.getMemoryScope())
                .executionType(run.getExecutionType())
                .status(run.getStatus())
                .output(run.getOutput())
                .errorMessage(run.getErrorMessage())
                .currentStep(run.getCurrentStep())
                .attemptCount(run.getAttemptCount())
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .build();
    }
}
