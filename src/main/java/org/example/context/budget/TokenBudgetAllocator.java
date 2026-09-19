package org.example.context.budget;

import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TokenBudgetAllocator {
    public Map<String, Integer> allocate(int available) {
        Map<String, Integer> budget = new LinkedHashMap<>();
        budget.put("summary", available * 15 / 100);
        budget.put("history", available * 35 / 100);
        budget.put("semantic", available * 15 / 100);
        budget.put("rag", available * 20 / 100);
        budget.put("skills", available * 15 / 100);
        return budget;
    }
}
