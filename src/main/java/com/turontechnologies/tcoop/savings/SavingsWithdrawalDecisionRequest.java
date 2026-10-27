package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** An admin's decision on a member's withdrawal request. There's no transfer reference — approving
 * pays the member out server-side (see SavingsSelfServiceController.decideWithdrawal). */
public record SavingsWithdrawalDecisionRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Approved|Declined", message = "Invalid decision")
        String status) {}
