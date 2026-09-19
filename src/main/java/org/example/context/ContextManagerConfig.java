package org.example.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scholarmind.context")
@Getter
@Setter
public class ContextManagerConfig {
    private int maxTokens = 16000;
    private int outputReserve = 2000;
    private int toolReserve = 3000;
    private int maxHistoryMessages = 12;
    private int summaryTokens = 1200;
    private boolean summaryModelEnabled = true;
    private int ragTopK = 3;

    public int inputLimit() {
        int limit = maxTokens - outputReserve - toolReserve;
        if (limit < 256 || summaryTokens < 64 || maxHistoryMessages < 2 || outputReserve < 1 || toolReserve < 256) {
            throw new IllegalStateException("Invalid ScholarMind context budget configuration");
        }
        return limit;
    }
}
