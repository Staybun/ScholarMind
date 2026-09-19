package org.example.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.memory.working.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisWorkingMemoryTest {
    @SuppressWarnings("unchecked")
    @Test
    void serializesVersionedSnapshotWithTtl() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        ObjectMapper mapper = new ObjectMapper();
        WorkingMemoryItem item = new WorkingMemoryItem("session-a", "scope-a", "summary", 2, 4, 7, List.of());
        when(operations.get(anyString())).thenReturn(mapper.writeValueAsString(item));
        RedisWorkingMemoryService service = new RedisWorkingMemoryService(redis, mapper, true, 60);
        service.put(item);
        verify(operations).set(anyString(), eq(mapper.writeValueAsString(item)), eq(Duration.ofSeconds(60)));
        assertEquals(item, service.get("session-a").orElseThrow());
    }

    @Test
    void connectionFailureOpensCooldownAndReturnsCacheMiss() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        RedisWorkingMemoryService service = new RedisWorkingMemoryService(redis, new ObjectMapper(), true, 60);
        assertTrue(service.get("session-a").isEmpty());
        assertTrue(service.get("session-a").isEmpty());
        verify(redis, times(1)).opsForValue();
    }
}
