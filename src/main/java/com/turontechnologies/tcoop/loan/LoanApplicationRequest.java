package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LoanApplicationRequest(
    @NotBlank(message = "Select a loan type") String loanTypeId,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount,
    @NotBlank(message = "Select a guarantor") String guarantorMemberId) {}
