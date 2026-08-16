package com.turontechnologies.tcoop.subscription;

import java.time.LocalDate;

/** Response for POST /cooperatives/:id/subscriptions — the payment plus what it bought. */
public record SubscriptionPaymentCreatedDto(
    SubscriptionPaymentDto payment, LocalDate nextRenewalDate) {}
