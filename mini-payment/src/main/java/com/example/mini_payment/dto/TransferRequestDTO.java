package com.example.mini_payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferRequestDTO {

    @NotBlank(message = "Tài khoản nguồn không được để trống")
    private String sourceAccountNumber;

    @NotBlank(message = "Tài khoản đích không được để trống")
    private String targetAccountNumber;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "1000.00", message = "Số tiền tối thiểu phải là 1,000 VND")
    private BigDecimal amount;

    private String idempotencyKey;
}