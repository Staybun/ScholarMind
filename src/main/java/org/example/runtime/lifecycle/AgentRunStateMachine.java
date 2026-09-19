package org.example.runtime.lifecycle;

import org.example.runtime.model.AgentRunStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

@Component
public class AgentRunStateMachine {

    private static final Map<AgentRunStatus, EnumSet<AgentRunStatus>> TRANSITIONS = Map.of(
            AgentRunStatus.PENDING, EnumSet.of(AgentRunStatus.RUNNING),
            AgentRunStatus.RUNNING, EnumSet.of(AgentRunStatus.RETRYING, AgentRunStatus.COMPLETED, AgentRunStatus.FAILED),
            AgentRunStatus.RETRYING, EnumSet.of(AgentRunStatus.RUNNING, AgentRunStatus.FAILED),
            AgentRunStatus.FAILED, EnumSet.of(AgentRunStatus.RUNNING),
            AgentRunStatus.COMPLETED, EnumSet.noneOf(AgentRunStatus.class)
    );

    public void validate(AgentRunStatus current, AgentRunStatus target) {
        if (current == target) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(current, EnumSet.noneOf(AgentRunStatus.class)).contains(target)) {
            throw new IllegalStateException("Invalid Agent Run transition: " + current + " -> " + target);
        }
    }
}

