package org.example.controller;

import org.example.runtime.AgentRuntime;
import org.example.runtime.checkpoint.CheckpointService;
import org.example.runtime.model.AgentRunRequest;
import org.example.runtime.model.AgentRunResult;
import org.example.runtime.persistence.AgentCheckpointEntity;
import org.example.runtime.persistence.AgentTraceEntity;
import org.example.runtime.trace.AgentTraceRecorder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {

    private final AgentRuntime agentRuntime;
    private final CheckpointService checkpointService;
    private final AgentTraceRecorder traceRecorder;

    public AgentRunController(AgentRuntime agentRuntime,
                              CheckpointService checkpointService,
                              AgentTraceRecorder traceRecorder) {
        this.agentRuntime = agentRuntime;
        this.checkpointService = checkpointService;
        this.traceRecorder = traceRecorder;
    }

    @PostMapping
    public ResponseEntity<AgentRunResult> create(@RequestBody AgentRunRequest request) {
        return ResponseEntity.ok(agentRuntime.start(request));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<AgentRunResult> get(@PathVariable String runId) {
        return ResponseEntity.ok(agentRuntime.get(runId));
    }

    @PostMapping("/{runId}/resume")
    public ResponseEntity<AgentRunResult> resume(@PathVariable String runId) {
        return ResponseEntity.ok(agentRuntime.resume(runId));
    }

    @GetMapping("/{runId}/checkpoints")
    public ResponseEntity<List<AgentCheckpointEntity>> checkpoints(@PathVariable String runId) {
        agentRuntime.get(runId);
        return ResponseEntity.ok(checkpointService.list(runId));
    }

    @GetMapping("/{runId}/traces")
    public ResponseEntity<List<AgentTraceEntity>> traces(@PathVariable String runId) {
        agentRuntime.get(runId);
        return ResponseEntity.ok(traceRecorder.list(runId));
    }
}

