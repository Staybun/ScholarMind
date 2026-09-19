package org.example.context.compact;

import org.example.context.budget.TokenEstimator;
import org.example.context.model.ContextMessage;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SlidingWindowStrategy {
    private final TokenEstimator estimator;
    public SlidingWindowStrategy(TokenEstimator estimator) { this.estimator = estimator; }

    public Window select(List<ContextMessage> messages, int tokenBudget, int maxMessages) {
        int start = messages.size();
        int used = 0;
        while (start > 0 && messages.size() - start < maxMessages) {
            int cost = estimator.estimate(messages.get(start - 1).render());
            if (used + cost > tokenBudget) break;
            used += cost;
            start--;
        }
        // Avoid keeping an assistant response without its preceding user turn.
        if (start < messages.size() && start > 0 && "assistant".equals(messages.get(start).role())) start++;
        return new Window(List.copyOf(messages.subList(start, messages.size())),
                List.copyOf(messages.subList(0, start)));
    }

    public record Window(List<ContextMessage> retained, List<ContextMessage> removed) { }
}
