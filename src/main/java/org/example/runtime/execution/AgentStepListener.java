package org.example.runtime.execution;

public interface AgentStepListener {
    default void contextAssembled(org.example.context.model.ContextBundle context) { }
    void started(String stepName);

    void completed(String stepName, String checkpointPayload);
}
