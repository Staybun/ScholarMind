package org.example.context.budget;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.example.context.ContextManagerConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContextBudgetInterceptor extends ModelInterceptor {
    private static final String TRUNCATED = "\n[TOOL_RESULT_TRUNCATED: retrieve a narrower range before asserting missing details]";
    private final ContextManagerConfig config;
    private final TokenEstimator estimator;

    public ContextBudgetInterceptor(ContextManagerConfig config, TokenEstimator estimator) {
        this.config = config; this.estimator = estimator;
    }

    @Override
    public String getName() { return "scholarmind_context_budget"; }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        config.inputLimit();
        List<Message> messages = boundToolResults(request.getMessages());
        // RC2's copy builder omits systemMessage; preserve it explicitly.
        ModelRequest bounded = ModelRequest.builder(request).systemMessage(request.getSystemMessage()).messages(messages).build();
        int estimated = estimateRequest(bounded);
        if (estimated > config.getMaxTokens() - config.getOutputReserve()) {
            throw new IllegalStateException("Agent model context exceeded token budget; narrow the task or tool query");
        }
        return handler.call(bounded);
    }

    private List<Message> boundToolResults(List<Message> messages) {
        long count = messages.stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).mapToLong(m -> m.getResponses().size()).sum();
        if (count == 0) return messages;
        int allowance = Math.max(0, config.getToolReserve() / (int) count - 32);
        List<Message> result = new ArrayList<>();
        for (Message message : messages) {
            if (!(message instanceof ToolResponseMessage tool)) { result.add(message); continue; }
            List<ToolResponseMessage.ToolResponse> responses = tool.getResponses().stream().map(response -> {
                String data = response.responseData();
                if (estimator.estimate(data) > allowance) {
                    data = estimator.truncate(data, Math.max(0, allowance - estimator.estimate(TRUNCATED))) + TRUNCATED;
                }
                return new ToolResponseMessage.ToolResponse(response.id(), response.name(), data);
            }).toList();
            result.add(ToolResponseMessage.builder().responses(responses).metadata(tool.getMetadata()).build());
        }
        return result;
    }

    private int estimateRequest(ModelRequest request) {
        int tokens = 128 + estimator.estimate(request.getSystemMessage() == null ? "" : request.getSystemMessage().getText());
        for (Message message : request.getMessages()) {
            tokens += 16 + estimator.estimate(message.getText());
            if (message instanceof ToolResponseMessage tool) {
                for (var response : tool.getResponses()) tokens += estimator.estimate(response.responseData())
                        + estimator.estimate(response.id()) + estimator.estimate(response.name());
            }
            if (message instanceof AssistantMessage assistant) {
                for (var call : assistant.getToolCalls()) tokens += estimator.estimate(call.arguments())
                        + estimator.estimate(call.id()) + estimator.estimate(call.name());
            }
        }
        if (request.getDynamicToolCallbacks() != null) {
            for (var callback : request.getDynamicToolCallbacks()) {
                var definition = callback.getToolDefinition();
                tokens += estimator.estimate(definition.name() + definition.description() + definition.inputSchema());
            }
        }
        if (request.getToolDescriptions() != null) tokens += estimator.estimate(request.getToolDescriptions().toString());
        return tokens;
    }
}
