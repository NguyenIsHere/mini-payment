package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.*;
import com.example.mini_payment.exception.InsufficientBalanceException;
import com.example.mini_payment.repository.AccountRepository;
import com.example.mini_payment.repository.OutboxEventRepository;
import com.example.mini_payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCoreServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentCoreService paymentCoreService;

    private Account acc001;
    private Account acc002;

    @BeforeEach
    void setUp() {
        acc001 = Account.builder()
                .id(1L)
                .accountNumber("ACC001")
                .ownerName("Sender")
                .balance(new BigDecimal("500000.00"))
                .build();

        acc002 = Account.builder()
                .id(2L)
                .accountNumber("ACC002")
                .ownerName("Receiver")
                .balance(new BigDecimal("100000.00"))
                .build();
    }

    @Test
    @DisplayName("Transfer updates balances, saves transaction, and writes pending outbox event")
    void transferMoneyInDB_Success_UpdatesBalancesAndSavesOutbox() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        request.setTargetAccountNumber("ACC002");
        request.setAmount(new BigDecimal("150000.00"));
        request.setIdempotencyKey("idem-key-001");

        when(accountRepository.findWithLockByAccountNumber("ACC001")).thenReturn(Optional.of(acc001));
        when(accountRepository.findWithLockByAccountNumber("ACC002")).thenReturn(Optional.of(acc002));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        Transaction result = paymentCoreService.transferMoneyInDB(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.SUCCESS, result.getStatus());
        assertEquals("idem-key-001", result.getIdempotencyKey());
        assertEquals(new BigDecimal("350000.00"), acc001.getBalance());
        assertEquals(new BigDecimal("250000.00"), acc002.getBalance());

        // Verify pessimistic lock order is strictly alphabetical (ACC001 then ACC002)
        InOrder lockOrder = inOrder(accountRepository);
        lockOrder.verify(accountRepository).findWithLockByAccountNumber("ACC001");
        lockOrder.verify(accountRepository).findWithLockByAccountNumber("ACC002");

        // Verify outbox event is recorded in the same transaction
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();
        assertEquals(OutboxStatus.PENDING, savedOutbox.getStatus());
        assertEquals(0, savedOutbox.getRetryCount());
        assertTrue(savedOutbox.getPayload().contains("idem-key-001"));
    }

    @Test
    @DisplayName("Reverse transfer (ACC002 -> ACC001) still locks ACC001 first to prevent deadlock")
    void transferMoneyInDB_ReverseDirection_LocksAccountsAlphabetically() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC002");
        request.setTargetAccountNumber("ACC001");
        request.setAmount(new BigDecimal("40000.00"));

        when(accountRepository.findWithLockByAccountNumber("ACC001")).thenReturn(Optional.of(acc001));
        when(accountRepository.findWithLockByAccountNumber("ACC002")).thenReturn(Optional.of(acc002));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentCoreService.transferMoneyInDB(request);

        // Lock acquisition must remain ACC001 -> ACC002 regardless of transfer direction
        InOrder lockOrder = inOrder(accountRepository);
        lockOrder.verify(accountRepository).findWithLockByAccountNumber("ACC001");
        lockOrder.verify(accountRepository).findWithLockByAccountNumber("ACC002");

        assertEquals(new BigDecimal("60000.00"), acc002.getBalance());
        assertEquals(new BigDecimal("540000.00"), acc001.getBalance());
    }

    @Test
    @DisplayName("Transfer throws IllegalArgumentException when source and target accounts are identical")
    void transferMoneyInDB_SameAccount_ThrowsIllegalArgumentException() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        request.setTargetAccountNumber("ACC001");
        request.setAmount(new BigDecimal("10000.00"));

        assertThrows(IllegalArgumentException.class, () -> paymentCoreService.transferMoneyInDB(request));
        verifyNoInteractions(accountRepository, transactionRepository, outboxEventRepository);
    }

    @Test
    @DisplayName("Transfer throws InsufficientBalanceException when source balance is lower than amount")
    void transferMoneyInDB_InsufficientBalance_ThrowsInsufficientBalanceException() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        request.setTargetAccountNumber("ACC002");
        request.setAmount(new BigDecimal("900000.00"));

        when(accountRepository.findWithLockByAccountNumber("ACC001")).thenReturn(Optional.of(acc001));
        when(accountRepository.findWithLockByAccountNumber("ACC002")).thenReturn(Optional.of(acc002));

        assertThrows(InsufficientBalanceException.class, () -> paymentCoreService.transferMoneyInDB(request));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, outboxEventRepository);
    }

    @Test
    @DisplayName("Transfer throws IllegalArgumentException when target account does not exist")
    void transferMoneyInDB_AccountNotFound_ThrowsIllegalArgumentException() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        request.setTargetAccountNumber("ACC999");
        request.setAmount(new BigDecimal("10000.00"));

        when(accountRepository.findWithLockByAccountNumber("ACC001")).thenReturn(Optional.of(acc001));
        when(accountRepository.findWithLockByAccountNumber("ACC999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> paymentCoreService.transferMoneyInDB(request));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, outboxEventRepository);
    }

    @Test
    @DisplayName("Recover method throws IllegalStateException after retry exhaustion")
    void recover_RetryExhausted_ThrowsIllegalStateException() {
        TransferRequestDTO request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        RuntimeException rootCause = new RuntimeException("Lock timeout");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> paymentCoreService.recover(rootCause, request)
        );

        assertTrue(ex.getMessage().contains("ACC001"));
        assertEquals(rootCause, ex.getCause());
    }
}
