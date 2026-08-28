package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        String withdrawalFeeType) {}
