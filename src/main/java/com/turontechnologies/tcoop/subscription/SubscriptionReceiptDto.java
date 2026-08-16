package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Everything a receipt (on-screen or downloaded PDF) needs — never persisted as a file, always
 * regenerable client-side from this plus a history row. */
public record SubscriptionReceiptDto(
    String coopId,
    String coopName,
    String adminName,
    String paymentRef,
    BigDecimal amountPaid,
    String method,
    LocalDate date,
    String type,
    String cycle,
    String status,
    LocalDate nextRenewalDate) {}
