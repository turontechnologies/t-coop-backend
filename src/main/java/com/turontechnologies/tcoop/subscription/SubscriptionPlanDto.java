package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;

public record SubscriptionPlanDto(
    String id, String type, String label, int durationInDays, BigDecimal amount, String status) {

  static SubscriptionPlanDto from(SubscriptionPlan plan) {
    return new SubscriptionPlanDto(
        plan.getId().toString(),
        plan.getType(),
        plan.getLabel(),
        plan.getDurationInDays(),
        plan.getAmount(),
        plan.getStatus());
  }
}
