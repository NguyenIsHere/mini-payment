package com.example.mini_payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequestDTO {

    @NotBlank(message = "Source account cannot be empty!")
    private String sourceAccountNumber;

    @NotBlank(message = "Target account cannot be empty!")
    private String targetAccountNumber;

    @NotNull(message = "Amount cannot be empty!")
    @DecimalMin(value = "1000.00", message = "Min amount is 1,000 VND")
    private BigDecimal amount;

    private String idempotencyKey;
}