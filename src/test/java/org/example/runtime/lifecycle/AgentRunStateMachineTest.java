package org.example.runtime.lifecycle;

import org.example.runtime.model.AgentRunStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRunStateMachineTest {

    private final AgentRunStateMachine stateMachine = new AgentRunStateMachine();

    @Test
    void acceptsNormalExecutionAndResumeTransitions() {
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.PENDING, AgentRunStatus.RUNNING));
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.RUNNING, AgentRunStatus.RETRYING));
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.RETRYING, AgentRunStatus.RUNNING));
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.RUNNING, AgentRunStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.FAILED, AgentRunStatus.RUNNING));
        assertDoesNotThrow(() -> stateMachine.validate(AgentRunStatus.RUNNING, AgentRunStatus.COMPLETED));
    }

    @Test
    void rejectsRestartingCompletedRun() {
        assertThrows(IllegalStateException.class,
                () -> stateMachine.validate(AgentRunStatus.COMPLETED, AgentRunStatus.RUNNING));
    }
}

