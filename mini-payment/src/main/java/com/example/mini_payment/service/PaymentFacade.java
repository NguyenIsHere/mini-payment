package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import com.example.mini_payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final StringRedisTemplate redisTemplate;
    private final PaymentCoreService paymentCoreService;
    private final TransactionRepository transactionRepository;
    private final RedissonClient redissonClient;

    public Transaction processTransfer(TransferRequestDTO request) {
        String idempotencyKey = request.getIdempotencyKey();
        String redisKey;

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisKey = "IDEMPOTENCY:" + idempotencyKey;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(5));

            if (Boolean.FALSE.equals(isNew)) {
                String currentStatus = redisTemplate.opsForValue().get(redisKey);
                if ("SUCCESS".equals(currentStatus)) {
                    return transactionRepository.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow(() -> new IllegalStateException("Completed transaction not found for key: " + idempotencyKey));
                }
                throw new IllegalStateException("Transaction is currently being processed: idempotencyKey=" + idempotencyKey);
            }
        } else {
            redisKey = null;
        }

        String lockKey = "LOCK:ACCOUNT:" + request.getSourceAccountNumber();
        RLock redisLock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            isLocked = redisLock.tryLock(3, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new IllegalStateException("Account is busy with another transaction: " + request.getSourceAccountNumber());
            }

            Transaction tx = paymentCoreService.transferMoneyInDB(request);

            if (redisKey != null) {
                redisTemplate.opsForValue().set(redisKey, "SUCCESS", Duration.ofHours(24));
            }
            return tx;
        } catch (DataIntegrityViolationException e) {
            if (redisKey != null) {
                return transactionRepository.findByIdempotencyKey(idempotencyKey)
                        .map(existingTx -> {
                            redisTemplate.opsForValue().set(redisKey, "SUCCESS", Duration.ofHours(24));
                            return existingTx;
                        })
                        .orElseThrow(() -> e);
            }
            throw e;
        } catch (InterruptedException e) {
            deleteIdempotencyKeyQuietly(redisKey);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring lock for account: " + request.getSourceAccountNumber(), e);
        } catch (Exception e) {
            deleteIdempotencyKeyQuietly(redisKey);
            throw e;
        } finally {
            if (isLocked && redisLock.isHeldByCurrentThread()) {
                redisLock.unlock();
            }
        }
    }

    private void deleteIdempotencyKeyQuietly(String redisKey) {
        if (redisKey != null) {
            redisTemplate.delete(redisKey);
        }
    }
}