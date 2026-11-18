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
    @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {}
