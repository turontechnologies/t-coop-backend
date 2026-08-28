package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** {@code interestAmount} is optional — null (or omitted) whenever {@code interestType} is
 * "NoInterest", the co-op's way of offering a genuinely interest-free loan. {@link LoanType}
 * normalizes a null amount to {@link BigDecimal#ZERO} either way. */
public record LoanTypeCreateRequest(
    @NotBlank(message = "Enter a loan type name") String name,
    @NotNull(message = "Enter an eligibility percentage") @Positive(message = "Enter an eligibility percentage")
        BigDecimal eligibilityPercent,
    @Positive(message = "Select a duration") int durationMonths,
    @NotNull(message = "Enter a maximum loan amount") @Positive(message = "Enter a maximum loan amount")
        BigDecimal maxAmount,
    @NotBlank(message = "Select a repayment interval") String repaymentInterval,
    @Positive(message = "Enter the number of installments") int numberOfInstallments,
    @NotBlank(message = "Select an interest type")
        @Pattern(regexp = "Percentage|Fixed|NoInterest", message = "Select a valid interest type")
        String interestType,
    @PositiveOrZero(message = "Enter an interest amount of 0 or more") BigDecimal interestAmount) {}
