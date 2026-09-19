package org.example.runtime.execution;

import org.example.runtime.model.AgentRunRequest;

public record AgentExecutionContext(String runId,
                                    AgentRunRequest request,
                                    String checkpointStep,
                                    String checkpointPayload,
                                    AgentStepListener stepListener) {
}
