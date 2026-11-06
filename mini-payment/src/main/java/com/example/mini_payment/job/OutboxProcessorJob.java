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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessorJob {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionSearchRepository transactionSearchRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000)
    public void processPendingOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findAndLockPendingEvents(50);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[OUTBOX JOB] Tìm thấy và khóa thành công {} events chờ đồng bộ", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            processSingleEvent(event);
        }
    }

    @Transactional
    public void processSingleEvent(OutboxEvent event) {
        try {
            event.setStatus(OutboxStatus.PROCESSING);
            outboxEventRepository.save(event);

            Transaction tx = objectMapper.readValue(event.getPayload(), Transaction.class);

            TransactionDocument doc = TransactionDocument.builder()
                    .id(String.valueOf(tx.getId()))
                    .transactionCode(tx.getTransactionCode())
                    .sourceAccountNumber(tx.getSourceAccountNumber())
                    .targetAccountNumber(tx.getTargetAccountNumber())
                    .amount(tx.getAmount())
                    .status(tx.getStatus().name())
                    .createdAt(tx.getCreatedAt())
                    .build();

            transactionSearchRepository.save(doc);

            event.setStatus(OutboxStatus.DONE);
            outboxEventRepository.save(event);
            log.info("[OUTBOX JOB] Đồng bộ thành công giao dịch {}", tx.getTransactionCode());

        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            log.error("[OUTBOX JOB] Lỗi đồng bộ event ID {}. Lần thử thứ: {}", event.getId(), event.getRetryCount(), e);

            if (event.getRetryCount() >= 5) {
                log.error("[OUTBOX JOB] Event ID {} vượt quá 5 lần retry. Đánh dấu FAILED_NEEDS_MANUAL_FIX", event.getId());
                event.setStatus(OutboxStatus.FAILED_NEEDS_MANUAL_FIX);
            } else {
                event.setStatus(OutboxStatus.PENDING);
            }
            outboxEventRepository.save(event);
        }
    }
}