package org.example.runtime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.runtime.model.AgentExecutionType;
import org.example.runtime.model.AgentRunRequest;
import org.example.service.ChatService;
import org.example.service.ResearchWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanExecuteAgentExecutorTest {

    @Test
    void resumesAfterPaperIndexWithoutRepeatingCompletedSteps() throws Exception {
        ChatService chatService = mock(ChatService.class);
        ResearchWorkflowService workflowService = mock(ResearchWorkflowService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        org.example.context.ContextManager contexts = mock(org.example.context.ContextManager.class);
        PlanExecuteAgentExecutor executor = new PlanExecuteAgentExecutor(chatService, workflowService, objectMapper, contexts);
        when(chatService.createResearchWorkflowModel()).thenReturn(mock(DashScopeChatModel.class));
        when(chatService.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        ResearchWorkflowProgress checkpoint = new ResearchWorkflowProgress();
        checkpoint.setTask("task");
        checkpoint.setPlan("saved plan");
        checkpoint.setPaperIndexResult("saved evidence");
        String payload = objectMapper.writeValueAsString(checkpoint);

        when(workflowService.executePhase(org.mockito.ArgumentMatchers.eq("research_chat_agent"), any(), any(), any()))
                .thenReturn("research answer");
        when(workflowService.executePhase(org.mockito.ArgumentMatchers.eq("experiment_planner_agent"), any(), any(), any()))
                .thenReturn("experiment plan");
        when(workflowService.composeFinalReport(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("final report");

        AgentRunRequest request = new AgentRunRequest();
        request.setExecutionType(AgentExecutionType.PLAN_EXECUTE);
        request.setInput("task");
        List<String> completedSteps = new ArrayList<>();
        AgentStepListener listener = new AgentStepListener() {
            @Override
            public void started(String stepName) {
            }

            @Override
            public void completed(String stepName, String checkpointPayload) {
                completedSteps.add(stepName);
            }
        };

        AgentExecutionResult result = executor.execute(new AgentExecutionContext(
                "run-1", request, "PAPER_INDEX_AGENT", payload, listener));

        assertEquals("final report", result.output());
        assertEquals(List.of("RESEARCH_CHAT_AGENT", "EXPERIMENT_PLANNER_AGENT"), completedSteps);
        verify(workflowService, never()).createExecutionPlan(anyString());
        verify(workflowService, never()).executePaperIndexPhase(any(), any(), anyString(), anyString());
        assertTrue(result.checkpointPayload().contains("final report"));
    }
}
