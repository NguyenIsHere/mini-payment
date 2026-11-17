package com.example.mini_payment.service;

import com.example.mini_payment.dto.CreateAccountRequestDTO;
import com.example.mini_payment.entity.Account;
import com.example.mini_payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(CreateAccountRequestDTO request) {
        String normalizedAccountNumber = request.getAccountNumber().trim();

        if (accountRepository.existsByAccountNumber(normalizedAccountNumber)) {
            throw new IllegalArgumentException("Account number already exists: " + normalizedAccountNumber);
        }

        BigDecimal startingBalance = request.getInitialBalance() != null
                ? request.getInitialBalance()
                : BigDecimal.ZERO;

        Account account = Account.builder()
                .accountNumber(normalizedAccountNumber)
                .ownerName(request.getOwnerName().trim())
                .balance(startingBalance)
                .build();

        try {
            Account savedAccount = accountRepository.saveAndFlush(account);
            log.info("Account created: accountNumber={}, initialBalance={}",
                    savedAccount.getAccountNumber(), savedAccount.getBalance());
            return savedAccount;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Account number already exists: " + normalizedAccountNumber, ex);
        }
    }

    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }
}