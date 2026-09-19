package org.example.runtime.execution;

import org.example.runtime.model.AgentExecutionType;
import org.springframework.stereotype.Component;

@Component
public class MultiAgentExecutor implements AgentExecutor {

    private final PlanExecuteAgentExecutor delegate;

    public MultiAgentExecutor(PlanExecuteAgentExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public AgentExecutionType executionType() {
        return AgentExecutionType.MULTI_AGENT;
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) throws Exception {
        return delegate.execute(context);
    }
}
