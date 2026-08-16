package com.turontechnologies.tcoop.subscription;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/subscriptions/me — an admin's own co-op's subscription standing and options. */
public record MySubscriptionDto(
    String coopId,
    String coopName,
    String adminName,
    String status,
    String subscriptionCycle,
    LocalDate subscriptionExpiresAt,
    List<SubscriptionPlanDto> availablePlans,
    List<GatewayOption> availableGateways) {

  /** publicKey is safe to expose — it's the whole point of a "public" key. Never the secret. */
  public record GatewayOption(String gateway, String publicKey) {}
}
