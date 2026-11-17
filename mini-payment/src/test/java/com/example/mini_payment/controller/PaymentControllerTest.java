package com.example.mini_payment.controller;

import com.example.mini_payment.config.TraceIdFilter;
import com.example.mini_payment.dto.TransferRequestDTO;
import com.example.mini_payment.entity.Transaction;
import com.example.mini_payment.entity.TransactionStatus;
import com.example.mini_payment.exception.GlobalExceptionHandler;
import com.example.mini_payment.exception.InsufficientBalanceException;
import com.example.mini_payment.service.PaymentFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentFacade paymentFacade;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payments/transfer returns 200 OK and echoes custom X-Trace-Id")
    void transfer_ValidRequestWithTraceId_Returns200OkAndTraceHeader() throws Exception {
        Transaction transaction = Transaction.builder()
                .id(1L)
                .transactionCode("tx-999")
                .sourceAccountNumber("ACC001")
                .targetAccountNumber("ACC002")
                .amount(new BigDecimal("50000.00"))
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey("idem-001")
                .build();

        when(paymentFacade.processTransfer(any(TransferRequestDTO.class))).thenReturn(transaction);

        String requestJson = """
                {
                  "sourceAccountNumber": "ACC001",
                  "targetAccountNumber": "ACC002",
                  "amount": 50000.00,
                  "idempotencyKey": "idem-001"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-Trace-Id", "trace-custom-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-custom-01"))
                .andExpect(jsonPath("$.transactionCode").value("tx-999"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/transfer generates X-Trace-Id when header is omitted")
    void transfer_MissingTraceIdHeader_GeneratesTraceId() throws Exception {
        Transaction transaction = Transaction.builder()
                .transactionCode("tx-888")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(paymentFacade.processTransfer(any(TransferRequestDTO.class))).thenReturn(transaction);

        String requestJson = """
                {
                  "sourceAccountNumber": "ACC001",
                  "targetAccountNumber": "ACC002",
                  "amount": 5000.00
                }
                """;

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("POST /api/v1/payments/transfer returns 400 Bad Request when amount is below 1000")
    void transfer_AmountBelowMinimum_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "sourceAccountNumber": "ACC001",
                  "targetAccountNumber": "ACC002",
                  "amount": 500.00
                }
                """;

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("POST /api/v1/payments/transfer maps InsufficientBalanceException to 400 Bad Request")
    void transfer_InsufficientBalance_Returns400BadRequest() throws Exception {
        when(paymentFacade.processTransfer(any(TransferRequestDTO.class)))
                .thenThrow(new InsufficientBalanceException("Insufficient balance in source account."));

        String requestJson = """
                {
                  "sourceAccountNumber": "ACC001",
                  "targetAccountNumber": "ACC002",
                  "amount": 999000.00
                }
                """;

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient balance"))
                .andExpect(jsonPath("$.message").value("Insufficient balance in source account."));
    }
}
