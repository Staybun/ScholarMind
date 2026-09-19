package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.CodeRepositoryTools;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.FileSystemTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.SkillTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {
    @Autowired
    private org.example.context.budget.ContextBudgetInterceptor contextBudget;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private FileSystemTools fileSystemTools;

    @Autowired
    private CodeRepositoryTools codeRepositoryTools;

    @Autowired
    private SkillTools skillTools;

    @Autowired
    private SkillRegistryService skillRegistryService;

    @Autowired
    private ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;
    @Value("${scholarmind.context.output-reserve:2000}")
    private int outputTokenReserve;

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, outputTokenReserve, 0.9);
    }

    public DashScopeChatModel createResearchWorkflowModel() {
        return createChatModel(createDashScopeApi(), 0.3, outputTokenReserve, 0.9);
    }

    public String buildBaseSystemPrompt() { return buildSystemPrompt(List.of()); }

    /**
     * 构建系统提示词（包含历史消息）
     * @param history 历史消息列表
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();
        
        systemPromptBuilder.append("""
                你是 ScholarMind 的 ReAct 问答 Agent，目标是在回答前主动选择合适工具获取证据，减少幻觉。

                工具选择策略：
                1. 用户询问上传论文的内容、方法、实验、指标、结论、局限性、相关工作或需要论文依据时，优先调用 queryPaperKnowledge。
                2. 用户要求读取项目文件、配置、Markdown、上传的文本材料，或给出了明确文件路径时，调用 readWorkspaceFile；路径不确定时先调用 listWorkspaceFiles。
                3. 用户询问本项目代码实现、类、方法、接口、配置项、目录结构或代码仓库内容时，调用 searchCodeRepository；需要完整文件上下文时再调用 readWorkspaceFile。
                4. 用户询问当前时间时，调用 getCurrentDateTime。
                5. 高频论文精读、论文卡片、结构化总结任务，先使用 listScholarMindSkills / readScholarMindSkill 查看 paper-reading Skill。
                6. 实验复现、复现计划、实现拆解、消融实验任务，先使用 listScholarMindSkills / readScholarMindSkill 查看 experiment-reproduction Skill。
                7. 如果外部 MCP 工具可用：
                   - 需要访问项目根目录之外的文件时，使用 filesystem MCP；项目内文件优先使用 readWorkspaceFile/listWorkspaceFiles。
                   - 需要查询远程 GitHub 仓库、Issue、PR、提交或远程代码时，使用 GitHub MCP；本地仓库代码优先使用 searchCodeRepository。
                   - 需要检索尚未上传到本地知识库的外部论文时，使用 arXiv MCP；已上传论文优先使用 queryPaperKnowledge。

                %s

                回答规则：
                - 论文结论必须来自 queryPaperKnowledge 返回的证据，并标注论文标题、章节、页码或文件名。
                - 代码结论必须来自 searchCodeRepository/readWorkspaceFile 的结果，并标注文件路径和行号或路径。
                - 文件内容问题必须基于 readWorkspaceFile 的结果。
                - 如果工具没有返回足够证据，直接说明“当前证据不足”，不要编造。
                - 可以给出推理过程的简短摘要，但不要虚构工具没有返回的信息。

                """.formatted(skillRegistryService.buildPromptSummary()));
        
        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }
        
        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");
        
        return systemPromptBuilder.toString();
    }

    public Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, internalDocsTools, fileSystemTools, codeRepositoryTools, skillTools};
    }

    /**
     * 获取 MCP 服务提供的工具回调列表。
     */
    public ToolCallback[] getToolCallbacks() {
        ToolCallbackProvider provider = toolCallbackProvider.getIfAvailable();
        if (provider == null) {
            return new ToolCallback[0];
        }
        return provider.getToolCallbacks();
    }

    /**
     * 记录可用 MCP 工具列表。
     */
    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = getToolCallbacks();
        logger.info("可用 MCP 工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("scholarmind_react_qa_agent")
                .interceptors(contextBudget)
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
