package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;

public record ConfirmLoanRepaymentRequest(
    @NotBlank(message = "Missing payment reference") String reference) {}
