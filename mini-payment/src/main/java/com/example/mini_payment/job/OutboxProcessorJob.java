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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessorJob {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionSearchRepository transactionSearchRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 10000)
    public void processPendingOutboxEvents() {
        List<OutboxEvent> claimedEvents = claimBatch();
        for (OutboxEvent event : claimedEvents) {
            syncSingleEvent(event);
        }
    }

    private List<OutboxEvent> claimBatch() {
        List<OutboxEvent> claimed = transactionTemplate.execute(status -> {
            List<OutboxEvent> events = outboxEventRepository.findAndLockPendingEvents(BATCH_SIZE);
            if (events.isEmpty()) {
                return Collections.emptyList();
            }
            events.forEach(event -> event.setStatus(OutboxStatus.PROCESSING));
            return outboxEventRepository.saveAll(events);
        });
        return claimed != null ? claimed : Collections.emptyList();
    }

    private void syncSingleEvent(OutboxEvent event) {
        try {
            Transaction tx = objectMapper.readValue(event.getPayload(), Transaction.class);
            transactionSearchRepository.save(TransactionDocument.from(tx));

            event.setStatus(OutboxStatus.DONE);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            int nextRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(nextRetryCount);

            if (nextRetryCount >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED_NEEDS_MANUAL_FIX);
                log.error("Outbox event moved to dead-letter after {} retries: eventId={}",
                        MAX_RETRIES, event.getId(), e);
            } else {
                event.setStatus(OutboxStatus.PENDING);
            }

            outboxEventRepository.save(event);
        }
    }
}