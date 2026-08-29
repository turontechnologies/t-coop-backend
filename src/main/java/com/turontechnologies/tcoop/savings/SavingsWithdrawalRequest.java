package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** {@code savingsTypeId} null means "Total Savings" — withdrawing across every type, matching
 * the frontend's existing "Total Savings (all types)" option. */
public record SavingsWithdrawalRequest(
    String savingsTypeId,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount,
    String note) {}
