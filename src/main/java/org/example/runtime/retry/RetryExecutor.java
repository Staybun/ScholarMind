package org.example.runtime.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

@Component
public class RetryExecutor {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double multiplier;
    private final long maxBackoffMs;

    public RetryExecutor(
            @Value("${scholarmind.runtime.retry.max-attempts:3}") int maxAttempts,
            @Value("${scholarmind.runtime.retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${scholarmind.runtime.retry.multiplier:2.0}") double multiplier,
            @Value("${scholarmind.runtime.retry.max-backoff-ms:5000}") long maxBackoffMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
        this.multiplier = Math.max(1.0, multiplier);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
    }

    public <T> T execute(Callable<T> action, IntConsumer beforeAttempt,
                         BiConsumer<Integer, Exception> afterFailure) throws Exception {
        long backoff = initialBackoffMs;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            beforeAttempt.accept(attempt);
            try {
                return action.call();
            } catch (Exception exception) {
                lastFailure = exception;
                afterFailure.accept(attempt, exception);
                if (attempt < maxAttempts && backoff > 0) {
                    Thread.sleep(backoff);
                    backoff = Math.min(maxBackoffMs, Math.round(backoff * multiplier));
                }
            }
        }
        throw lastFailure;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
