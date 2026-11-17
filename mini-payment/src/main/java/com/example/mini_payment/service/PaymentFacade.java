package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final StringRedisTemplate redisTemplate;
    private final PaymentCoreService paymentCoreService;
    private final RedissonClient redissonClient;

    public Transaction processTransfer(TransferRequestDTO request) {
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = "IDEMPOTENCY:" + idempotencyKey;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(5));

            if (Boolean.FALSE.equals(isNew)) {
                throw new IllegalArgumentException("Duplicate request: idempotencyKey=" + idempotencyKey);
            }
        }

        String lockKey = "LOCK:ACCOUNT:" + request.getSourceAccountNumber();
        RLock redisLock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            isLocked = redisLock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new IllegalStateException("Account is busy with another transaction: " + request.getSourceAccountNumber());
            }

            Transaction tx = paymentCoreService.transferMoneyInDB(request);

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                redisTemplate.opsForValue().set("IDEMPOTENCY:" + idempotencyKey, "SUCCESS", Duration.ofHours(24));
            }

            return tx;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring lock for account: " + request.getSourceAccountNumber(), e);
        } finally {
            if (isLocked && redisLock.isHeldByCurrentThread()) {
                redisLock.unlock();
            }
        }
    }
}