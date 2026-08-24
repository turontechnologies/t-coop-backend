package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record LoanTypeCreateRequest(
    @NotBlank(message = "Enter a loan type name") String name,
    @NotNull(message = "Enter an eligibility percentage") @Positive(message = "Enter an eligibility percentage")
        BigDecimal eligibilityPercent,
    @Positive(message = "Select a duration") int durationMonths,
    @NotNull(message = "Enter a maximum loan amount") @Positive(message = "Enter a maximum loan amount")
        BigDecimal maxAmount,
    @NotBlank(message = "Select a repayment interval") String repaymentInterval,
    @Positive(message = "Enter the number of installments") int numberOfInstallments,
    @NotBlank(message = "Select an interest type") String interestType,
    @NotNull(message = "Enter an interest amount") @PositiveOrZero(message = "Enter an interest amount of 0 or more")
        BigDecimal interestAmount) {}
