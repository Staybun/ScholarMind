package org.example.runtime.execution;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.example.runtime.model.AgentExecutionType;
import org.example.service.ChatService;
import org.example.context.ContextManager;
import org.example.context.model.ContextBundle;
import org.springframework.stereotype.Component;

@Component
public class ReactAgentExecutor implements AgentExecutor {

    private final ChatService chatService;
    private final ContextManager contexts;

    public ReactAgentExecutor(ChatService chatService, ContextManager contexts) {
        this.chatService = chatService;
        this.contexts = contexts;
    }

    @Override
    public AgentExecutionType executionType() {
        return AgentExecutionType.REACT;
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) throws Exception {
        DashScopeApi api = chatService.createDashScopeApi();
        DashScopeChatModel model = chatService.createStandardChatModel(api);
        ContextBundle assembled = contexts.prepare(context.request(), chatService.buildBaseSystemPrompt(), null);
        context.stepListener().contextAssembled(assembled);
        ReactAgent agent = chatService.createReactAgent(model, assembled.systemPrompt());
        return AgentExecutionResult.completed(chatService.executeChat(agent, assembled.input()));
    }
}
