package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
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
                log.warn("Phát hiện request trùng lặp qua IdempotencyKey: {}", idempotencyKey);
                throw new IllegalArgumentException("Giao dịch đang được xử lý hoặc đã hoàn thành.");
            }
        }

        String lockKey = "LOCK:ACCOUNT:" + request.getSourceAccountNumber();
        RLock redisLock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            isLocked = redisLock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("Không thể lấy được Redis Lock cho tài khoản {} do đang có giao dịch khác xử lý", request.getSourceAccountNumber());
                throw new IllegalStateException("Hệ thống đang bận xử lý giao dịch cho tài khoản này, vui lòng thử lại sau.");
            }

            log.info("Đã lấy được Redis Distributed Lock cho tài khoản: {}", request.getSourceAccountNumber());

            Transaction tx = paymentCoreService.transferMoneyInDB(request);

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String redisKey = "IDEMPOTENCY:" + idempotencyKey;
                redisTemplate.opsForValue().set(redisKey, "SUCCESS", Duration.ofHours(24));
            }

            return tx;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Luồng thực thi bị gián đoạn khi xin khóa Redis", e);
        } finally {
            if (isLocked && redisLock.isHeldByCurrentThread()) {
                redisLock.unlock();
                log.info("Đã giải phóng Redis Distributed Lock cho tài khoản: {}", request.getSourceAccountNumber());
            }
        }
    }
}