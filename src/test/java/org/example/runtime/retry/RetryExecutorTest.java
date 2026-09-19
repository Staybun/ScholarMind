package org.example.runtime.retry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryExecutorTest {

    @Test
    void retriesFailedActionAndReturnsSuccessfulResult() throws Exception {
        RetryExecutor executor = new RetryExecutor(3, 0, 2.0, 0);
        AtomicInteger calls = new AtomicInteger();
        List<Integer> attempts = new ArrayList<>();
        List<Integer> failures = new ArrayList<>();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary failure");
            }
            return "ok";
        }, attempts::add, (attempt, exception) -> failures.add(attempt));

        assertEquals("ok", result);
        assertEquals(List.of(1, 2, 3), attempts);
        assertEquals(List.of(1, 2), failures);
    }

    @Test
    void throwsAfterMaximumAttempts() {
        RetryExecutor executor = new RetryExecutor(2, 0, 2.0, 0);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("still failing");
        }, attempt -> { }, (attempt, exception) -> { }));

        assertEquals(2, calls.get());
    }
}
