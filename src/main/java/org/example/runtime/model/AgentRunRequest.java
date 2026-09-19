package org.example.runtime.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AgentRunRequest {
    private AgentExecutionType executionType;
    private String input;
    private String sessionId;
    private String memoryScope;
    private List<Map<String, String>> history = new ArrayList<>();
}
