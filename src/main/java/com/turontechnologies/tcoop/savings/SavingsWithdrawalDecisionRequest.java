package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** An admin's decision on a member's withdrawal request. There's no transfer reference — approving
 * pays the member out server-side (see SavingsSelfServiceController.decideWithdrawal). A decline
 * can say why; the member is told in their notification. */
public record SavingsWithdrawalDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Declined", message = "Invalid decision")
        String status,
    @Size(max = 500, message = "Keep the reason under 500 characters") String reason,
    /**
     * How an approved withdrawal is paid: "Online" (the platform pays the member's bank now,
     * the default), or how the co-op already paid it itself — "Transfer", "Cheque" or "Cash".
     */
    @Pattern(regexp = "Online|Transfer|Cheque|Cash", message = "Choose online, bank transfer, cheque or cash")
        String method,
    @Size(max = 500) String receiptUrl,
    @Size(max = 100) String reference) {}
