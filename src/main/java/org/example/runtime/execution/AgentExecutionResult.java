package org.example.runtime.execution;

public record AgentExecutionResult(String output, String checkpointPayload) {
    public static AgentExecutionResult completed(String output) {
        return new AgentExecutionResult(output, output);
    }
}

