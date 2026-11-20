package com.example.mini_payment.service;

import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import com.example.mini_payment.entity.TransactionStatus;
import com.example.mini_payment.exception.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PaymentCoreService paymentCoreService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock redisLock;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private TransferRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new TransferRequestDTO();
        request.setSourceAccountNumber("ACC001");
        request.setTargetAccountNumber("ACC002");
        request.setAmount(new BigDecimal("50000.00"));
        request.setIdempotencyKey("idem-123");
    }

    @Test
    @DisplayName("Process transfer checks idempotency, acquires distributed lock, executes core transfer, and unlocks")
    void processTransfer_ValidRequestWithIdempotency_ExecutesAndUnlocks() throws InterruptedException {
        Transaction expectedTx = Transaction.builder()
                .id(1L)
                .transactionCode("tx-uuid-1")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("IDEMPOTENCY:idem-123", "PROCESSING", Duration.ofMinutes(5)))
                .thenReturn(Boolean.TRUE);
        when(redissonClient.getLock("LOCK:ACCOUNT:ACC001")).thenReturn(redisLock);
        when(redisLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(redisLock.isHeldByCurrentThread()).thenReturn(true);
        when(paymentCoreService.transferMoneyInDB(request)).thenReturn(expectedTx);

        Transaction result = paymentFacade.processTransfer(request);

        assertEquals("tx-uuid-1", result.getTransactionCode());
        verify(valueOperations).set("IDEMPOTENCY:idem-123", "SUCCESS", Duration.ofHours(24));
        verify(redisLock).unlock();
    }

    @Test
    @DisplayName("Process transfer rejects duplicate idempotency key before acquiring lock")
    void processTransfer_DuplicateIdempotencyKey_ThrowsIllegalArgumentException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("IDEMPOTENCY:idem-123", "PROCESSING", Duration.ofMinutes(5)))
                .thenReturn(Boolean.FALSE);

        assertThrows(IllegalArgumentException.class, () -> paymentFacade.processTransfer(request));
        verifyNoInteractions(redissonClient, paymentCoreService);
    }

    @Test
    @DisplayName("Process transfer skips idempotency check when idempotencyKey is null")
    void processTransfer_NullIdempotencyKey_SkipsRedisIdempotency() throws InterruptedException {
        request.setIdempotencyKey(null);
        Transaction expectedTx = Transaction.builder().transactionCode("tx-no-idem").build();

        when(redissonClient.getLock("LOCK:ACCOUNT:ACC001")).thenReturn(redisLock);
        when(redisLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(redisLock.isHeldByCurrentThread()).thenReturn(true);
        when(paymentCoreService.transferMoneyInDB(request)).thenReturn(expectedTx);

        Transaction result = paymentFacade.processTransfer(request);

        assertEquals("tx-no-idem", result.getTransactionCode());
        verifyNoInteractions(redisTemplate);
        verify(redisLock).unlock();
    }

    @Test
    @DisplayName("Process transfer throws IllegalStateException when Redis lock times out")
    void processTransfer_LockTimeout_ThrowsIllegalStateException() throws InterruptedException {
        request.setIdempotencyKey(null);
        when(redissonClient.getLock("LOCK:ACCOUNT:ACC001")).thenReturn(redisLock);
        when(redisLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> paymentFacade.processTransfer(request));
        verifyNoInteractions(paymentCoreService);
        verify(redisLock, never()).unlock();
    }

    @Test
    @DisplayName("Process transfer always releases Redis lock when core service throws exception")
    void processTransfer_CoreServiceFails_ReleasesRedisLock() throws InterruptedException {
        request.setIdempotencyKey(null);
        when(redissonClient.getLock("LOCK:ACCOUNT:ACC001")).thenReturn(redisLock);
        when(redisLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(redisLock.isHeldByCurrentThread()).thenReturn(true);
        when(paymentCoreService.transferMoneyInDB(request))
                .thenThrow(new InsufficientBalanceException("Insufficient balance"));

        assertThrows(InsufficientBalanceException.class, () -> paymentFacade.processTransfer(request));
        verify(redisLock).unlock();
    }
}
