package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.SkillTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import org.example.context.model.ContextBundle;

/**
 * ScholarMind multi-agent workflow service.
 *
 * Coordinates the ScholarMind paper research workflow.
 */
@Service
public class ResearchWorkflowService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.example.context.budget.ContextBudgetInterceptor contextBudget;

    private static final Logger logger = LoggerFactory.getLogger(ResearchWorkflowService.class);

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools paperKnowledgeTools;

    @Autowired
    private SkillTools skillTools;

    public Optional<OverAllState> executeResearchWorkflow(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            String userTask) throws GraphRunnerException {

        logger.info("开始执行 ScholarMind 多 Agent 论文研究工作流，任务: {}", userTask);

        ReactAgent paperIndexAgent = buildPaperIndexAgent(chatModel, toolCallbacks);
        ReactAgent researchChatAgent = buildResearchChatAgent(chatModel, toolCallbacks);
        ReactAgent experimentPlannerAgent = buildExperimentPlannerAgent(chatModel, toolCallbacks);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("scholarmind_plan_executor_supervisor")
                .description("Plan-Executor 控制器，协调论文索引、研究问答和实验复现规划 Agent")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(paperIndexAgent, researchChatAgent, experimentPlannerAgent))
                .build();

        String taskPrompt = """
                你正在执行 ScholarMind 论文研究任务。
                用户任务：%s

                请使用 Plan-Executor 模式：
                1. 先规划需要检索哪些论文证据、回答哪些研究问题、是否需要实验复现方案。
                2. 调用 Paper Index Agent 检索和整理论文片段。
                3. 调用 Research Chat Agent 基于论文证据回答和归纳。
                4. 调用 Experiment Planner Agent 把论文方法转成可执行实验复现计划。
                5. 汇总为一份 Markdown 结果，必须标注证据来源；证据不足时明确说明。
                """.formatted(userTask);

        return supervisorAgent.invoke(taskPrompt);
    }

    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("开始提取 ScholarMind 多 Agent 最终结果");

        Optional<String> experimentPlan = extractText(state, "experiment_plan");
        if (experimentPlan.isPresent() && !experimentPlan.get().isBlank()) {
            return experimentPlan;
        }

        Optional<String> researchAnswer = extractText(state, "research_answer");
        if (researchAnswer.isPresent() && !researchAnswer.get().isBlank()) {
            return researchAnswer;
        }

        Optional<String> paperIndex = extractText(state, "paper_index_result");
        if (paperIndex.isPresent() && !paperIndex.get().isBlank()) {
            return paperIndex;
        }

        logger.warn("未能从 workflow state 中提取最终结果");
        return Optional.empty();
    }

    private Optional<String> extractText(OverAllState state, String key) {
        return state.value(key)
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .map(AssistantMessage::getText);
    }

    private ReactAgent buildPaperIndexAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("paper_index_agent")
                .description("负责检索论文知识库，整理相关论文片段、章节、页码和证据覆盖度")
                .model(chatModel)
                .systemPrompt(buildPaperIndexPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("paper_index_result")
                .build();
    }

    private ReactAgent buildResearchChatAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("research_chat_agent")
                .description("负责基于论文证据进行研究问答、方法总结、差异比较和局限性分析")
                .model(chatModel)
                .systemPrompt(buildResearchChatPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("research_answer")
                .build();
    }

    private ReactAgent buildExperimentPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("experiment_planner_agent")
                .description("负责把论文方法和实验设定转化为可执行复现实验计划")
                .model(chatModel)
                .systemPrompt(buildExperimentPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("experiment_plan")
                .build();
    }

    private Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, paperKnowledgeTools, skillTools};
    }

    public String createExecutionPlan(String task) {
        return """
                1. Paper Index Agent：围绕任务检索论文知识库并整理可引用证据。
                2. Research Chat Agent：基于检索证据回答、总结和比较研究问题。
                3. Experiment Planner Agent：基于论文证据与研究结论生成复现实验计划。

                原始任务：%s
                """.formatted(task);
    }

    public String phasePrompt(String phase) {
        return switch (phase) {
            case "paper_index_agent" -> buildPaperIndexPrompt();
            case "research_chat_agent" -> buildResearchChatPrompt();
            case "experiment_planner_agent" -> buildExperimentPlannerPrompt();
            default -> throw new IllegalArgumentException("Unknown research phase: " + phase);
        };
    }

    public String executePhase(String phase, DashScopeChatModel model, ToolCallback[] tools,
                               ContextBundle context) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder().name(phase).model(model).systemPrompt(context.systemPrompt())
                .interceptors(contextBudget)
                .methodTools(buildMethodToolsArray()).tools(tools).build();
        return agent.call(context.input()).getText();
    }

    public String executePaperIndexPhase(DashScopeChatModel chatModel,
                                         ToolCallback[] toolCallbacks,
                                         String task,
                                         String plan) throws GraphRunnerException {
        ReactAgent agent = buildPaperIndexAgent(chatModel, toolCallbacks);
        return agent.call("""
                用户研究任务：%s

                执行计划：
                %s

                现在只执行论文检索和证据整理阶段。
                """.formatted(task, plan)).getText();
    }

    public String executeResearchChatPhase(DashScopeChatModel chatModel,
                                           ToolCallback[] toolCallbacks,
                                           String task,
                                           String paperIndexResult) throws GraphRunnerException {
        ReactAgent agent = buildResearchChatAgent(chatModel, toolCallbacks);
        return agent.call("""
                用户研究任务：%s

                Paper Index Agent 已检索到的证据：
                %s

                请严格基于以上证据完成研究问答阶段。
                """.formatted(task, paperIndexResult)).getText();
    }

    public String executeExperimentPlannerPhase(DashScopeChatModel chatModel,
                                                ToolCallback[] toolCallbacks,
                                                String task,
                                                String paperIndexResult,
                                                String researchAnswer) throws GraphRunnerException {
        ReactAgent agent = buildExperimentPlannerAgent(chatModel, toolCallbacks);
        return agent.call("""
                用户研究任务：%s

                Paper Index Agent 证据：
                %s

                Research Chat Agent 结论：
                %s

                请据此生成可执行的实验复现计划。
                """.formatted(task, paperIndexResult, researchAnswer)).getText();
    }

    public String composeFinalReport(String plan,
                                     String paperIndexResult,
                                     String researchAnswer,
                                     String experimentPlan) {
        return """
                # ScholarMind 多 Agent 研究结果

                ## 执行计划
                %s

                ## Paper Index Agent 证据摘要
                %s

                ## Research Chat Agent 研究结论
                %s

                ## Experiment Planner Agent 复现实验计划
                %s
                """.formatted(plan, paperIndexResult, researchAnswer, experimentPlan);
    }

    private String buildPaperIndexPrompt() {
        return """
                你是 Paper Index Agent，负责论文知识库检索与证据整理。

                职责：
                1. 根据输入任务拆出 3-6 个检索查询。
                2. 如任务是论文精读或结构化总结，先读取 paper-reading Skill。
                3. 使用 queryPaperKnowledge 工具检索 ScholarMind 论文知识库。
                4. 合并重复片段，保留论文标题、文件名、章节、页码、chunkIndex 和关键摘录。
                5. 判断证据覆盖度：SUFFICIENT、PARTIAL、INSUFFICIENT。
                6. 不回答超出检索证据的问题，不编造论文内容。

                输出 Markdown，包含：
                ## 检索计划
                ## TopK 证据片段
                ## 证据覆盖度
                ## 建议交给 Research Chat Agent 的问题
                """;
    }

    private String buildResearchChatPrompt() {
        return """
                你是 Research Chat Agent，负责基于论文证据进行研究问答。

                职责：
                1. 优先读取上下文中 Paper Index Agent 的检索结果。
                2. 对论文精读、论文卡片、结构化总结任务，读取并遵循 paper-reading Skill。
                3. 如证据不足，可以继续使用 queryPaperKnowledge 补充检索。
                4. 回答论文方法、核心贡献、实验设置、指标结果、局限性和相关工作差异。
                5. 每个关键结论必须标注证据来源：论文标题 / 章节 / 页码或文件名。
                6. 对证据不足的部分明确写“当前知识库证据不足”。

                输出 Markdown，包含：
                ## 研究问题回答
                ## 方法与贡献总结
                ## 实验证据
                ## 局限性与不确定点
                ## 可交给 Experiment Planner Agent 的复现要点
                """;
    }

    private String buildExperimentPlannerPrompt() {
        return """
                你是 Experiment Planner Agent，负责把论文方法转成可执行复现实验计划。

                职责：
                1. 读取上下文中 Paper Index Agent 的证据和 Research Chat Agent 的分析。
                2. 读取并遵循 experiment-reproduction Skill。
                3. 提炼复现目标、数据集、预处理、模型/算法、训练或运行步骤、评价指标、消融实验和风险。
                4. 所有复现步骤必须区分“论文明确给出”“从证据推断”和“需要用户确认”。
                5. 不编造具体指标、超参数或数据集；缺失时列为待确认项。
                6. 最终输出即用户可执行的 ScholarMind 多 Agent 工作流结果。

                输出 Markdown，严格包含：
                # ScholarMind 多 Agent 研究结果
                ## 任务理解
                ## Paper Index Agent 证据摘要
                ## Research Chat Agent 研究结论
                ## Experiment Planner Agent 复现实验计划
                ## 待确认问题
                ## 下一步建议
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是 ScholarMind Plan-Executor Supervisor，负责协调三个子 Agent：
                - paper_index_agent：检索论文知识库并整理证据。
                - research_chat_agent：基于证据回答研究问题。
                - experiment_planner_agent：生成实验复现计划。

                工作规则：
                1. 必须先调用 paper_index_agent，获取论文证据。
                2. 如果用户任务包含“解释、总结、比较、问答、方法、贡献、局限”等，调用 research_chat_agent。
                3. 如果用户任务包含“复现、实验、计划、代码、数据集、指标、消融”等，调用 experiment_planner_agent。
                4. 默认完整流程为 paper_index_agent -> research_chat_agent -> experiment_planner_agent。
                5. 如果 Paper Index Agent 返回证据不足，仍可调用 Research Chat Agent 总结不足点，但最终必须明确证据不足。
                6. 最终输出必须是 Markdown，不能输出 JSON，不能编造论文事实。
                """;
    }
}
