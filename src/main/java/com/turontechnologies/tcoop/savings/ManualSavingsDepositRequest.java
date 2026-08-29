package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ManualSavingsDepositRequest(
    @NotBlank(message = "Select a member") String memberId,
    @NotBlank(message = "Select a savings type") String savingsTypeId,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount,
    String receiptUrl) {}
