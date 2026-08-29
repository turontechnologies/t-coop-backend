package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code transferReference} is required for an Approve — the frontend only calls this endpoint
 * after {@code initiateTransfer()} (the existing real Paystack Transfer route) has already
 * succeeded, and the reference it returns becomes this withdrawal's transaction id. */
public record SavingsWithdrawalDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Declined", message = "Invalid decision")
        String status,
    String transferReference) {}
