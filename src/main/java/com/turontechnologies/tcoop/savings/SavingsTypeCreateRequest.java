package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record SavingsTypeCreateRequest(
    @NotBlank(message = "Enter a savings type name") String name,
    @NotNull(message = "Enter a minimum amount") @PositiveOrZero(message = "Enter a minimum amount of 0 or more")
        BigDecimal minAmount,
    @NotNull(message = "Enter a maximum amount") @PositiveOrZero(message = "Enter a maximum amount of 0 or more")
        BigDecimal maxAmount) {}
