package com.example.mini_payment.job;

import com.example.mini_payment.document.TransactionDocument;
import com.example.mini_payment.entity.OutboxEvent;
import com.example.mini_payment.entity.OutboxStatus;
import com.example.mini_payment.entity.Transaction;
import com.example.mini_payment.repository.OutboxEventRepository;
import com.example.mini_payment.repository.TransactionSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessorJob {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionSearchRepository transactionSearchRepository;
    private final ObjectMapper objectMapper;

    // Chạy ngầm định kỳ mỗi 60 giây (60000 ms)
    @Scheduled(fixedDelay = 60000)
    public void processPendingOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        
        if (pendingEvents.isEmpty()) {
            return; // Không có việc thì nghỉ
        }
        
        log.info("[OUTBOX JOB] Tìm thấy {} events cần đồng bộ sang Elasticsearch", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // 1. Giải mã Payload từ JSON về lại Object Transaction
                Transaction tx = objectMapper.readValue(event.getPayload(), Transaction.class);

                // 2. Map sang Document của Elasticsearch
                TransactionDocument doc = TransactionDocument.builder()
                        .id(String.valueOf(tx.getId()))
                        .transactionCode(tx.getTransactionCode())
                        .sourceAccountNumber(tx.getSourceAccountNumber())
                        .targetAccountNumber(tx.getTargetAccountNumber())
                        .amount(tx.getAmount())
                        .status(tx.getStatus().name())
                        .createdAt(tx.getCreatedAt())
                        .build();

                // 3. Đẩy sang ES (I/O Network diễn ra ở đây, hoàn toàn tách biệt với DB Transaction)
                transactionSearchRepository.save(doc);

                // 4. Cập nhật thành công
                event.setStatus(OutboxStatus.DONE);
                outboxEventRepository.save(event);
                log.info("[OUTBOX JOB] Đồng bộ thành công giao dịch {}", tx.getTransactionCode());

            } catch (Exception e) {
                // 5. Xử lý kịch bản lỗi mạng, sập Elasticsearch
                event.setRetryCount(event.getRetryCount() + 1);
                log.error("[OUTBOX JOB] Đồng bộ lỗi event ID {}. Lần thử: {}", event.getId(), event.getRetryCount(), e);

                if (event.getRetryCount() >= 5) {
                    log.error("[OUTBOX JOB] Event ID {} đã vượt quá 5 lần retry. Đánh dấu CẦN CAN THIỆP THỦ CÔNG", event.getId());
                    event.setStatus(OutboxStatus.FAILED_NEEDS_MANUAL_FIX);
                }
                outboxEventRepository.save(event);
            }
        }
    }
}