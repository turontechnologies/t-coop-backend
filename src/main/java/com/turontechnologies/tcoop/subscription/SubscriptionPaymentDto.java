package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of a co-op's subscription payment history. */
public record SubscriptionPaymentDto(
    String id,
    String paymentRef,
    BigDecimal amountPaid,
    String method,
    LocalDate date,
    String type,
    String cycle,
    String status,
    LocalDate resultingExpiresAt) {

  static SubscriptionPaymentDto from(SubscriptionPayment payment) {
    return new SubscriptionPaymentDto(
        payment.getId().toString(),
        payment.getPaymentRef(),
        payment.getAmountPaid(),
        payment.getMethod(),
        payment.getPaymentDate(),
        payment.getType(),
        payment.getCycle(),
        payment.getStatus(),
        payment.getResultingExpiresAt());
  }
}
