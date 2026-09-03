package io.github.aminedelta.core_banking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryLock_returnsTrueWhenRedisCreatesKey() {
        when(valueOperations.setIfAbsent(
                "idempotency:key-1", "PENDING", 10, TimeUnit.SECONDS
        )).thenReturn(true);

        assertTrue(idempotencyService.tryLock("key-1"));
    }

    @Test
    void tryLock_returnsFalseWhenKeyAlreadyExists() {
        when(valueOperations.setIfAbsent(
                "idempotency:key-1", "PENDING", 10, TimeUnit.SECONDS
        )).thenReturn(false);

        assertFalse(idempotencyService.tryLock("key-1"));
    }

    @Test
    void tryLock_returnsFalseWhenRedisReturnsNull() {
        when(valueOperations.setIfAbsent(
                "idempotency:key-1", "PENDING", 10, TimeUnit.SECONDS
        )).thenReturn(null);

        assertFalse(idempotencyService.tryLock("key-1"));
    }

    @Test
    void saveReceipt_storesReceiptFor24Hours() {
        String receipt = "{\"status\":\"SUCCESS\"}";

        idempotencyService.saveReceipt("key-1", receipt);

        verify(valueOperations).set(
                "idempotency:key-1", receipt, 24, TimeUnit.HOURS
        );
    }

    @Test
    void getCachedReceipt_readsReceiptFromRedis() {
        String receipt = "{\"status\":\"SUCCESS\"}";
        when(valueOperations.get("idempotency:key-1")).thenReturn(receipt);

        assertEquals(receipt, idempotencyService.getCachedReceipt("key-1"));
    }

    @Test
    void release_deletesRedisKey() {
        idempotencyService.release("key-1");

        verify(redisTemplate).delete("idempotency:key-1");
    }
}
