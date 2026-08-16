package com.turontechnologies.tcoop.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A super admin records money already received (bank transfer, cheque, etc.) — not a payment
 * gateway call. {@code planId} picks the label/duration from the super admin's own Subscription
 * Plans catalog (Payment Settings); {@code amountPaid} stays free-typed since a real external
 * payment can legitimately differ from the catalog's listed price (partial, discounted, etc.).
 * Whether this is the co-op's first subscription or a renewal, and the resulting new expiry
 * date, are both computed server-side (see SubscriptionController) — never trusted from the
 * client.
 */
public record SubscriptionPaymentCreateRequest(
    @NotNull(message = "Enter the amount paid")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        java.math.BigDecimal amountPaid,
    @NotBlank(message = "Select a subscription plan") String planId) {}
