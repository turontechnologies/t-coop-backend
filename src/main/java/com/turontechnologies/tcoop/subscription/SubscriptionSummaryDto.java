package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the super admin's platform-wide GET /subscriptions table. */
public record SubscriptionSummaryDto(
    String coopId,
    String coopName,
    BigDecimal revenueEarned,
    BigDecimal subscriptionFee,
    String subscriptionCycle,
    LocalDate lastPaymentDate,
    LocalDate subscriptionExpiresAt,
    String status) {}
