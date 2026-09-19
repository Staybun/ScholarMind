package org.example.context.budget;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class TokenEstimator {
    // Conservative estimate, not a provider-specific tokenizer.
    public int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : (text.getBytes(StandardCharsets.UTF_8).length + 1) / 2;
    }

    public String truncate(String text, int budget) {
        if (text == null || budget <= 0) return "";
        if (estimate(text) <= budget) return text;
        int[] points = text.codePoints().toArray();
        int low = 0;
        int high = points.length;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (estimate(new String(points, 0, mid)) <= budget) low = mid;
            else high = mid - 1;
        }
        return new String(points, 0, low);
    }
}
