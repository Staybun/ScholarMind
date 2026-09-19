package org.example.memory.working;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class RedisWorkingMemoryService implements WorkingMemoryService {
    private static final Logger log = LoggerFactory.getLogger(RedisWorkingMemoryService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;
    private volatile long retryAfter;

    public RedisWorkingMemoryService(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${scholarmind.memory.working.enabled:true}") boolean enabled,
            @Value("${scholarmind.memory.working.ttl-seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    public Optional<WorkingMemoryItem> get(String sessionId) {
        if (!available()) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key(sessionId));
            return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, WorkingMemoryItem.class));
        } catch (Exception e) { unavailable(e); return Optional.empty(); }
    }

    public void put(WorkingMemoryItem item) {
        if (!available()) return;
        try { redis.opsForValue().set(key(item.sessionId()), mapper.writeValueAsString(item), ttl); }
        catch (Exception e) { unavailable(e); }
    }

    public void clear(String sessionId) {
        if (!available()) return;
        try { redis.delete(key(sessionId)); }
        catch (Exception e) { unavailable(e); }
    }

    private boolean available() { return enabled && System.currentTimeMillis() >= retryAfter; }
    private String key(String id) {
        return "scholarmind:working:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }
    private void unavailable(Exception e) {
        retryAfter = System.currentTimeMillis() + 30000;
        log.warn("Redis Working Memory unavailable; using persistent conversation memory: {}", e.getClass().getSimpleName());
    }
}
