package com.example.mini_payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequestDTO {

    @NotBlank(message = "Account number must not be blank")
    @Size(max = 32, message = "Account number must not exceed 32 characters")
    private String accountNumber;

    @NotBlank(message = "Owner name must not be blank")
    @Size(max = 100, message = "Owner name must not exceed 100 characters")
    private String ownerName;

    @DecimalMin(value = "0.00", message = "Initial balance must not be negative")
    private BigDecimal initialBalance;
}