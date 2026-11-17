package com.example.mini_payment.service;

import com.example.mini_payment.dto.CreateAccountRequestDTO;
import com.example.mini_payment.entity.Account;
import com.example.mini_payment.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("Create account trims whitespace and defaults null balance to zero")
    void createAccount_ValidRequestWithNullBalance_TrimsAndDefaultsToZero() {
        CreateAccountRequestDTO request = CreateAccountRequestDTO.builder()
                .accountNumber("  ACC010  ")
                .ownerName("  Nguyen Tran  ")
                .initialBalance(null)
                .build();

        when(accountRepository.existsByAccountNumber("ACC010")).thenReturn(false);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        Account result = accountService.createAccount(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("ACC010", result.getAccountNumber());
        assertEquals("Nguyen Tran", result.getOwnerName());
        assertEquals(BigDecimal.ZERO, result.getBalance());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(captor.capture());
        assertEquals("ACC010", captor.getValue().getAccountNumber());
    }

    @Test
    @DisplayName("Create account throws IllegalArgumentException when account already exists")
    void createAccount_DuplicateAccountNumber_ThrowsIllegalArgumentException() {
        CreateAccountRequestDTO request = CreateAccountRequestDTO.builder()
                .accountNumber("ACC001")
                .ownerName("Nguyen Tran")
                .initialBalance(new BigDecimal("50000.00"))
                .build();

        when(accountRepository.existsByAccountNumber("ACC001")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(request)
        );

        assertTrue(ex.getMessage().contains("ACC001"));
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Create account translates concurrent unique constraint violation to IllegalArgumentException")
    void createAccount_ConcurrentUniqueViolation_ThrowsIllegalArgumentException() {
        CreateAccountRequestDTO request = CreateAccountRequestDTO.builder()
                .accountNumber("ACC001")
                .ownerName("Nguyen Tran")
                .initialBalance(new BigDecimal("50000.00"))
                .build();

        when(accountRepository.existsByAccountNumber("ACC001")).thenReturn(false);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(request));
    }

    @Test
    @DisplayName("Get account by number returns account when found")
    void getAccountByNumber_ExistingAccount_ReturnsAccount() {
        Account account = Account.builder()
                .id(1L)
                .accountNumber("ACC001")
                .ownerName("Nguyen Tran")
                .balance(new BigDecimal("100000.00"))
                .build();

        when(accountRepository.findByAccountNumber("ACC001")).thenReturn(Optional.of(account));

        Account result = accountService.getAccountByNumber("  ACC001  ");

        assertEquals("ACC001", result.getAccountNumber());
        assertEquals(new BigDecimal("100000.00"), result.getBalance());
    }

    @Test
    @DisplayName("Get account by number throws IllegalArgumentException when missing")
    void getAccountByNumber_NotFound_ThrowsIllegalArgumentException() {
        when(accountRepository.findByAccountNumber("ACC999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> accountService.getAccountByNumber("ACC999"));
    }
}
