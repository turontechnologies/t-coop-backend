package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** {@code loanMode} is "TimesTwo" (default, two or more guarantors required) or "Within" (no
 * guarantor) — see LoanEligibility. {@code guarantorMemberIds} is ignored for a Within loan;
 * {@code guarantorMemberId} is the single-guarantor field older clients still send. */
public record LoanApplicationRequest(
    @NotBlank(message = "Select a loan type") String loanTypeId,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount,
    String loanMode,
    String guarantorMemberId,
    List<String> guarantorMemberIds) {}
