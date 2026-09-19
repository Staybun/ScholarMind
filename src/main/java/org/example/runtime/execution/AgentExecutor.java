package org.example.runtime.execution;

import org.example.runtime.model.AgentExecutionType;

public interface AgentExecutor {
    AgentExecutionType executionType();

    AgentExecutionResult execute(AgentExecutionContext context) throws Exception;
}

