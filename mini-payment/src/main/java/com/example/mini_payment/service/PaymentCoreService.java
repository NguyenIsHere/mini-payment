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
        if (request.getSourceAccountNumber().equals(request.getTargetAccountNumber())) {
            throw new IllegalArgumentException("Source and target accounts must be different.");
        }
        
        boolean isSourceFirst = request.getSourceAccountNumber().compareTo(request.getTargetAccountNumber()) < 0;
        String firstAccountNumber = isSourceFirst ? request.getSourceAccountNumber() : request.getTargetAccountNumber();
        String secondAccountNumber = isSourceFirst ? request.getTargetAccountNumber() : request.getSourceAccountNumber();

        Account firstAccount = accountRepository.findWithLockByAccountNumber(firstAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstAccountNumber));
        Account secondAccount = accountRepository.findWithLockByAccountNumber(secondAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondAccountNumber));

        Account sourceAccount = isSourceFirst ? firstAccount : secondAccount;
        Account targetAccount = isSourceFirst ? secondAccount : firstAccount;

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in source account.");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        targetAccount.setBalance(targetAccount.getBalance().add(request.getAmount()));

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        Transaction savedTransaction = transactionRepository.save(Transaction.builder()
                .transactionCode(UUID.randomUUID().toString())
                .sourceAccountNumber(request.getSourceAccountNumber())
                .targetAccountNumber(request.getTargetAccountNumber())
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(request.getIdempotencyKey())
                .build());

        outboxEventRepository.save(OutboxEvent.builder()
                .payload(serializeTransaction(savedTransaction))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());

        log.info("Transfer completed: txCode={}, from={}, to={}, amount={}",
                savedTransaction.getTransactionCode(),
                request.getSourceAccountNumber(),
                request.getTargetAccountNumber(),
                request.getAmount());

        return savedTransaction;
    }

    @Recover
    public Transaction recover(Exception e, TransferRequestDTO request) {
        throw new IllegalStateException("DB lock contention persisted after retries for sourceAccount="
                + request.getSourceAccountNumber(), e);
    }

    private String serializeTransaction(Transaction transaction) {
        try {
            return objectMapper.writeValueAsString(transaction);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize transaction: " + transaction.getTransactionCode(), e);
        }
    }
}