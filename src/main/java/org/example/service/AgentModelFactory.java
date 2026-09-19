package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentModelFactory {
    private final String apiKey;

    public AgentModelFactory(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public DashScopeChatModel summaryModel(int outputTokens) {
        if (apiKey == null || apiKey.isBlank() || "your-api-key-here".equals(apiKey)) {
            throw new IllegalStateException("DashScope API key is not configured");
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(0.1).withMaxToken(Math.max(128, outputTokens)).build())
                .build();
    }
}
