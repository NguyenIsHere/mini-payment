package com.example.mini_payment.controller;

import com.example.mini_payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/reconciliation/trigger")
    public ResponseEntity<String> triggerReconciliation() {
        log.info("[ADMIN API] Admin vừa yêu cầu chạy đối soát thủ công!");

        // Vì trong runDailyReconciliation chúng ta có lệnh .join() để đợi tất cả Thread chạy xong,
        // API này sẽ hold (chờ) cho đến khi đối soát hoàn tất mới trả về chữ "Hoàn tất".
        reconciliationService.runDailyReconciliation();

        return ResponseEntity.ok("Tiến trình đối soát đã chạy hoàn tất. Vui lòng kiểm tra log hệ thống.");
    }
}