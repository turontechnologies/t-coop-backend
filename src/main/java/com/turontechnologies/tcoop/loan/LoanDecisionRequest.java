package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code transferReference} is required for an Approve — same "transfer already happened"
 * contract as savings withdrawal approval: the frontend calls this only after the real Paystack
 * Transfer disbursement has already succeeded. */
public record LoanDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Rejected", message = "Invalid decision")
        String decision,
    String rejectionReason,
    String transferReference) {}
