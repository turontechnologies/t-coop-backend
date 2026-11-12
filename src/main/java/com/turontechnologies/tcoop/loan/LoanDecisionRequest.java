package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A committee member's decision on the loan's current step. Approving needs the amount and
 * repayment period they suggest; the last approver's figures are what the borrower is offered.
 */
public record LoanDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Rejected", message = "Invalid decision")
        String decision,
    @Size(max = 1000, message = "Keep the reason under 1,000 characters") String rejectionReason,
    BigDecimal suggestedAmount,
    @Min(value = 1, message = "The repayment period must be at least 1 month")
        @Max(value = 120, message = "The repayment period is too long")
        Integer suggestedMonths,
    @Size(max = 1000, message = "Keep the note under 1,000 characters") String note) {}
