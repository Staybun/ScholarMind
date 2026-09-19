package org.example.runtime.execution;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.runtime.model.AgentExecutionType;
import org.example.service.ChatService;
import org.example.service.ResearchWorkflowService;
import org.springframework.stereotype.Component;
import org.example.context.ContextManager;
import org.example.context.model.ContextBundle;

@Component
public class PlanExecuteAgentExecutor implements AgentExecutor {

    private final ChatService chatService;
    private final ResearchWorkflowService workflowService;
    private final ObjectMapper objectMapper;
    private final ContextManager contexts;

    public PlanExecuteAgentExecutor(ChatService chatService,
                                    ResearchWorkflowService workflowService,
                                    ObjectMapper objectMapper, ContextManager contexts) {
        this.chatService = chatService;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.contexts = contexts;
    }

    @Override
    public AgentExecutionType executionType() {
        return AgentExecutionType.PLAN_EXECUTE;
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) throws Exception {
        DashScopeChatModel model = chatService.createResearchWorkflowModel();
        ResearchWorkflowProgress progress = restoreProgress(context);
        String task = context.request().getInput();
        if (progress.getTask() == null) {
            progress.setTask(task);
        }

        if (progress.getPlan() == null) {
            runStep(context, "WORKFLOW_PLANNING");
            progress.setPlan(workflowService.createExecutionPlan(task));
            saveStep(context, "WORKFLOW_PLANNING", progress);
        }
        if (progress.getPaperIndexResult() == null) {
            runStep(context, "PAPER_INDEX_AGENT");
            progress.setPaperIndexResult(executePhase(context, model, "paper_index_agent", progress.getPlan()));
            saveStep(context, "PAPER_INDEX_AGENT", progress);
        }
        if (progress.getResearchAnswer() == null) {
            runStep(context, "RESEARCH_CHAT_AGENT");
            progress.setResearchAnswer(executePhase(context, model, "research_chat_agent", progress.getPaperIndexResult()));
            saveStep(context, "RESEARCH_CHAT_AGENT", progress);
        }
        if (progress.getExperimentPlan() == null) {
            runStep(context, "EXPERIMENT_PLANNER_AGENT");
            progress.setExperimentPlan(executePhase(context, model, "experiment_planner_agent",
                    progress.getPaperIndexResult() + "\n" + progress.getResearchAnswer()));
            saveStep(context, "EXPERIMENT_PLANNER_AGENT", progress);
        }

        String output = workflowService.composeFinalReport(progress.getPlan(),
                progress.getPaperIndexResult(), progress.getResearchAnswer(), progress.getExperimentPlan());
        return AgentExecutionResult.completed(output);
    }

    private ResearchWorkflowProgress restoreProgress(AgentExecutionContext context) {
        if (context.checkpointStep() == null || !context.checkpointStep().startsWith("WORKFLOW_")
                && !context.checkpointStep().endsWith("_AGENT")) {
            return new ResearchWorkflowProgress();
        }
        try {
            return objectMapper.readValue(context.checkpointPayload(), ResearchWorkflowProgress.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to restore research workflow checkpoint", exception);
        }
    }

    private void runStep(AgentExecutionContext context, String stepName) {
        context.stepListener().started(stepName);
    }

    private String executePhase(AgentExecutionContext context, DashScopeChatModel model,
                                 String phase, String evidence) throws Exception {
        ContextBundle assembled = contexts.prepare(context.request(), workflowService.phasePrompt(phase), evidence);
        context.stepListener().contextAssembled(assembled);
        return workflowService.executePhase(phase, model, chatService.getToolCallbacks(), assembled);
    }

    private void saveStep(AgentExecutionContext context, String stepName, ResearchWorkflowProgress progress) {
        try {
            context.stepListener().completed(stepName, objectMapper.writeValueAsString(progress));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save research workflow checkpoint", exception);
        }
    }
}
