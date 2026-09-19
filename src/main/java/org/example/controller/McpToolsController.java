package org.example.controller;

import lombok.Getter;
import lombok.Setter;
import org.example.service.ChatService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP tool introspection APIs.
 */
@RestController
@RequestMapping("/api/tools")
public class McpToolsController {

    @Autowired
    private ChatService chatService;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean mcpEnabled;

    @GetMapping("/mcp")
    public ResponseEntity<McpToolsResponse> listMcpTools() {
        ToolCallback[] callbacks = chatService.getToolCallbacks();
        List<McpToolInfo> tools = new ArrayList<>();

        for (ToolCallback callback : callbacks) {
            McpToolInfo info = new McpToolInfo();
            info.setName(callback.getToolDefinition().name());
            info.setDescription(callback.getToolDefinition().description());
            tools.add(info);
        }

        McpToolsResponse response = new McpToolsResponse();
        response.setMcpEnabled(mcpEnabled);
        response.setToolCount(tools.size());
        response.setTools(tools);
        return ResponseEntity.ok(response);
    }

    @Getter
    @Setter
    public static class McpToolsResponse {
        private boolean mcpEnabled;
        private int toolCount;
        private List<McpToolInfo> tools;
    }

    @Getter
    @Setter
    public static class McpToolInfo {
        private String name;
        private String description;
    }
}
