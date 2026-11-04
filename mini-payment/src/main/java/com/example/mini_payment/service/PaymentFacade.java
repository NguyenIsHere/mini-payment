package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final StringRedisTemplate redisTemplate;
    private final PaymentCoreService paymentCoreService;

    public Transaction processTransfer(TransferRequestDTO request) {
        String redisKey = "IDEMPOTENCY:" + request.getIdempotencyKey();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(5));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("Phát hiện request trùng lặp: {}", request.getIdempotencyKey());
            throw new IllegalArgumentException("Giao dịch đang được xử lý hoặc đã hoàn thành.");
        }

        try {
            // Gọi xuống lớp Core (lúc này DB Transaction mới thực sự bắt đầu)
            Transaction tx = paymentCoreService.transferMoneyInDB(request);

            // Core chạy xong và DB đã COMMIT thành công, giờ mới update Redis
            redisTemplate.opsForValue().set(redisKey, "SUCCESS", Duration.ofHours(24));
            return tx;

        } catch (Exception e) {
            // Lỗi xảy ra, DB đã ROLLBACK xong, giải phóng Redis
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}