package org.example.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.runtime.checkpoint.CheckpointService;
import org.example.runtime.execution.AgentExecutionContext;
import org.example.runtime.execution.AgentExecutionResult;
import org.example.runtime.execution.AgentExecutor;
import org.example.runtime.lifecycle.AgentRunManager;
import org.example.runtime.lifecycle.AgentRunStateMachine;
import org.example.runtime.model.AgentExecutionType;
import org.example.runtime.model.AgentRunRequest;
import org.example.runtime.model.AgentRunResult;
import org.example.runtime.model.AgentRunStatus;
import org.example.runtime.persistence.AgentCheckpointRepository;
import org.example.runtime.persistence.AgentRunRepository;
import org.example.runtime.persistence.AgentTraceRepository;
import org.example.runtime.retry.RetryExecutor;
import org.example.runtime.trace.AgentTraceRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AgentRuntimeIntegrationTest {

    @Autowired
    private AgentRunRepository runRepository;

    @Autowired
    private AgentCheckpointRepository checkpointRepository;

    @Autowired
    private AgentTraceRepository traceRepository;

    @Test
    void persistsFailureAndResumesRunFromCheckpoint() {
        AgentRunStateMachine stateMachine = new AgentRunStateMachine();
        AgentRunManager runManager = new AgentRunManager(runRepository, stateMachine);
        CheckpointService checkpointService = new CheckpointService(checkpointRepository);
        AgentTraceRecorder traceRecorder = new AgentTraceRecorder(traceRepository);
        RetryExecutor retryExecutor = new RetryExecutor(2, 0, 2.0, 0);
        AtomicInteger calls = new AtomicInteger();

        AgentExecutor executor = new AgentExecutor() {
            @Override
            public AgentExecutionType executionType() {
                return AgentExecutionType.REACT;
            }

            @Override
            public AgentExecutionResult execute(AgentExecutionContext context) {
                if (calls.incrementAndGet() <= 2) {
                    throw new IllegalStateException("temporary model failure");
                }
                return AgentExecutionResult.completed("resumed answer");
            }
        };

        AgentRuntime runtime = new AgentRuntime(runManager, checkpointService, traceRecorder,
                retryExecutor, new ObjectMapper().findAndRegisterModules(), List.of(executor),
                org.mockito.Mockito.mock(org.example.context.ContextManager.class));
        AgentRunRequest request = new AgentRunRequest();
        request.setExecutionType(AgentExecutionType.REACT);
        request.setInput("test question");

        AgentRunResult failed = runtime.start(request);
        assertEquals(AgentRunStatus.FAILED, failed.getStatus());
        assertEquals(2, failed.getAttemptCount());

        AgentRunResult resumed = runtime.resume(failed.getRunId());
        assertEquals(AgentRunStatus.COMPLETED, resumed.getStatus());
        assertEquals("resumed answer", resumed.getOutput());
        assertEquals(3, resumed.getAttemptCount());
        assertFalse(checkpointRepository.findByRunIdOrderBySequenceNumberAsc(failed.getRunId()).isEmpty());
        assertFalse(traceRepository.findByRunIdOrderByCreatedAtAsc(failed.getRunId()).isEmpty());
    }

    @Test
    void persistsStreamingRetryAndCompletion() {
        AgentRunManager runManager = new AgentRunManager(runRepository, new AgentRunStateMachine());
        CheckpointService checkpointService = new CheckpointService(checkpointRepository);
        AgentTraceRecorder traceRecorder = new AgentTraceRecorder(traceRepository);
        AgentRuntime runtime = new AgentRuntime(runManager, checkpointService, traceRecorder,
                new RetryExecutor(3, 0, 2.0, 0),
                new ObjectMapper().findAndRegisterModules(), List.of(),
                org.mockito.Mockito.mock(org.example.context.ContextManager.class));

        AgentRunRequest request = new AgentRunRequest();
        request.setExecutionType(AgentExecutionType.REACT);
        request.setInput("stream question");

        AgentRunResult started = runtime.beginStreaming(request);
        runtime.retryStreaming(started.getRunId(), new IllegalStateException("temporary stream failure"));
        AgentRunResult completed = runtime.completeStreaming(started.getRunId(), "stream answer");

        assertEquals(AgentRunStatus.COMPLETED, completed.getStatus());
        assertEquals(2, completed.getAttemptCount());
        assertEquals("stream answer", completed.getOutput());
        assertEquals(2, checkpointRepository.findByRunIdOrderBySequenceNumberAsc(started.getRunId()).size());
        assertTrue(traceRepository.findByRunIdOrderByCreatedAtAsc(started.getRunId()).stream()
                .anyMatch(trace -> "RETRY_SCHEDULED".equals(trace.getEventType())));
    }

    @Test
    void resumesMemoryPersistenceFailureWithoutRepeatingModelExecution() {
        org.example.context.ContextManager contexts = org.mockito.Mockito.mock(org.example.context.ContextManager.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable")).doNothing()
                .when(contexts).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        AtomicInteger modelCalls = new AtomicInteger();
        AgentExecutor executor = new AgentExecutor() {
            public AgentExecutionType executionType() { return AgentExecutionType.REACT; }
            public AgentExecutionResult execute(AgentExecutionContext context) {
                modelCalls.incrementAndGet();
                return AgentExecutionResult.completed("saved output");
            }
        };
        AgentRuntime runtime = new AgentRuntime(new AgentRunManager(runRepository, new AgentRunStateMachine()),
                new CheckpointService(checkpointRepository), new AgentTraceRecorder(traceRepository),
                new RetryExecutor(1, 0, 2.0, 0), new ObjectMapper().findAndRegisterModules(), List.of(executor), contexts);
        AgentRunRequest request = new AgentRunRequest();
        request.setExecutionType(AgentExecutionType.REACT); request.setInput("question");
        AgentRunResult failed = runtime.start(request);
        assertEquals(AgentRunStatus.FAILED, failed.getStatus());
        AgentRunResult resumed = runtime.resume(failed.getRunId());
        assertEquals(AgentRunStatus.COMPLETED, resumed.getStatus());
        assertEquals("saved output", resumed.getOutput());
        assertEquals(1, modelCalls.get());
    }
}
