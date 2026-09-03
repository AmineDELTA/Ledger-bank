package io.github.aminedelta.core_banking.service;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {
    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String idempotencyKey){
        String Rkey = "idempotency:" + idempotencyKey;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(Rkey, "PENDING", 10, TimeUnit.SECONDS);
        return success != null && success;
    }

    public void saveReceipt(String idempotencyKey, String jsonReceipt){
        String Rkey = "idempotency:" + idempotencyKey;
        redisTemplate.opsForValue().set(Rkey, jsonReceipt, 24, TimeUnit.HOURS);
    }

    public String getCachedReceipt(String idempotencyKey){
        return redisTemplate.opsForValue().get("idempotency:" + idempotencyKey);
    }

    public void release(String idempotencyKey){
        redisTemplate.delete("idempotency:" + idempotencyKey);
    }
}
