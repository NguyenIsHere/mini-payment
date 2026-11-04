package com.example.mini_payment.job;

import com.example.mini_payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;

    // Biểu thức Cron: Chạy vào lúc 02:00:00 sáng mỗi ngày
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduleDailyReconciliation() {
        log.info("[CRON JOB] Bắt đầu kích hoạt tự động tiến trình đối soát cuối ngày...");
        reconciliationService.runDailyReconciliation();
    }
}