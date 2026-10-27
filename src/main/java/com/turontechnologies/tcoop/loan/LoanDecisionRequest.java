package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** An officer's decision on the loan's current approval step. There's no transfer reference —
 * the Secretary's approval disburses server-side (see LoanSelfServiceController.decide). */
public record LoanDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Rejected", message = "Invalid decision")
        String decision,
    String rejectionReason) {}
