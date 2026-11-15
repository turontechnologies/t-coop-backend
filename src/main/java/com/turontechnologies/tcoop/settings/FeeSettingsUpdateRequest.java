package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Mirrors the frontend's feeChargesSchema exactly (src/lib/validations/settings.schema.ts). */
public record FeeSettingsUpdateRequest(
    @Pattern(regexp = "Fixed|Percentage", message = "Select a valid savings charge type")
        String savingsChargeType,
    @NotNull(message = "Enter an amount of 0 or more")
        @DecimalMin(value = "0", message = "Enter an amount of 0 or more")
        BigDecimal savingsChargeAmount,
    @Pattern(regexp = "Fixed|Percentage", message = "Select a valid loans charge type")
        String loansChargeType,
    @NotNull(message = "Enter an amount of 0 or more")
        @DecimalMin(value = "0", message = "Enter an amount of 0 or more")
        BigDecimal loansChargeAmount,
    @NotNull(message = "Enter an amount of 0 or more")
        @DecimalMin(value = "0", message = "Enter an amount of 0 or more")
        BigDecimal withdrawalFeeAmount,
    @Pattern(regexp = "Fixed|Percentage", message = "Select a valid withdrawal fee type")
        String withdrawalFeeType,
    @Pattern(regexp = "Fixed|Percentage", message = "Select a valid loan application fee type")
        String loanApplicationFeeType,
    @NotNull(message = "Enter an amount of 0 or more")
        @DecimalMin(value = "0", message = "Enter an amount of 0 or more")
        BigDecimal loanApplicationFeeAmount,
    @Min(value = 0, message = "Enter 0 or more days")
        @Max(value = 365, message = "The grace period can be at most 365 days")
        int subscriptionGraceDays) {}
