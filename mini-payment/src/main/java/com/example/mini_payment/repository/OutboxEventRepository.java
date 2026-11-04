package com.example.mini_payment.repository;

import com.example.mini_payment.entity.OutboxEvent;
import com.example.mini_payment.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    // Tìm các event đang PENDING để Cron Job xử lý
    List<OutboxEvent> findByStatus(OutboxStatus status);
}