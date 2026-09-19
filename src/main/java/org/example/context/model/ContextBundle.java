package org.example.context.model;

import java.util.List;
import java.util.Map;

public record ContextBundle(String systemPrompt, String input, int estimatedTokens,
                            int inputLimit, boolean compacted, int historyMessageCount,
                            List<String> selectedSkills, List<String> memoryIds,
                            List<String> evidenceIds, List<String> warnings,
                            Map<String, Integer> sectionTokens) {
}
