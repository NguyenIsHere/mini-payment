package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.*;
import com.example.mini_payment.exception.InsufficientBalanceException;
import com.example.mini_payment.repository.AccountRepository;
import com.example.mini_payment.repository.OutboxEventRepository;
import com.example.mini_payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCoreService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Retryable(
            retryFor = {
                    CannotAcquireLockException.class,
                    PessimisticLockingFailureException.class,
                    ObjectOptimisticLockingFailureException.class
            },
            maxAttempts = 4,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000, random = true)
    )
    @Transactional(rollbackFor = Exception.class)
    public Transaction transferMoneyInDB(TransferRequestDTO request) {
        log.info("Bắt đầu xử lý chuyển tiền trong DB từ {} sang {}", request.getSourceAccountNumber(), request.getTargetAccountNumber());

        if (request.getSourceAccountNumber().equals(request.getTargetAccountNumber())) {
            throw new IllegalArgumentException("Tài khoản nguồn và tài khoản đích không được trùng nhau");
        }

        // GIẢI PHÁP NGHẼN DEADLOCK: Sắp xếp thứ tự Lock theo bảng chữ cái số tài khoản
        boolean isSourceFirst = request.getSourceAccountNumber().compareTo(request.getTargetAccountNumber()) < 0;
        String firstAccNumber = isSourceFirst ? request.getSourceAccountNumber() : request.getTargetAccountNumber();
        String secondAccNumber = isSourceFirst ? request.getTargetAccountNumber() : request.getSourceAccountNumber();

        Account firstAccount = accountRepository.findWithLockByAccountNumber(firstAccNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + firstAccNumber));

        Account secondAccount = accountRepository.findWithLockByAccountNumber(secondAccNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + secondAccNumber));

        Account sourceAccount = isSourceFirst ? firstAccount : secondAccount;
        Account targetAccount = isSourceFirst ? secondAccount : firstAccount;

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Số dư tài khoản nguồn không đủ");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        targetAccount.setBalance(targetAccount.getBalance().add(request.getAmount()));

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        Transaction transaction = Transaction.builder()
                .transactionCode(UUID.randomUUID().toString())
                .sourceAccountNumber(request.getSourceAccountNumber())
                .targetAccountNumber(request.getTargetAccountNumber())
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        try {
            // OUTBOX PATTERN cho đồng bộ lịch sử data transaction sang elasticsearch
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .payload(objectMapper.writeValueAsString(savedTransaction))
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(outboxEvent);

            log.info("Đã ghi event vào bảng Outbox cho giao dịch: {}", savedTransaction.getTransactionCode());
        } catch (Exception e) {
            log.error("Lỗi khi parse JSON cho Outbox Event", e);
            throw new RuntimeException("Lỗi hệ thống khi ghi nhận Outbox");
        }

        return savedTransaction;
    }

    @Recover
    public Transaction recover(Exception e, TransferRequestDTO request) {
        log.error("Hệ thống quá tải Lock sau nhiều lần Retry. DB Transaction Rollback.", e);
        throw new IllegalStateException("Hệ thống đang nghẽn mạch cục bộ, vui lòng thực hiện lại giao dịch sau giây lát.");
    }
}