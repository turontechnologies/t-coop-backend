package com.turontechnologies.tcoop.coopgateway;

import java.time.LocalDateTime;

/**
 * What the co-op admin's Settings -> Integrations tab reads. Secret keys are never sent back to
 * the browser — only whether one is saved — unlike the platform's own IntegrationSettingsDto,
 * since a co-op's keys move real member money into the co-op's own account.
 */
public record CoopIntegrationsDto(
    boolean paystackEnabled,
    String paystackPublicKey,
    boolean paystackSecretKeySet,
    boolean flutterwaveEnabled,
    String flutterwavePublicKey,
    boolean flutterwaveSecretKeySet,
    LocalDateTime updatedAt) {

  public static CoopIntegrationsDto from(CooperativePaymentGateway gateway) {
    if (gateway == null) {
      return new CoopIntegrationsDto(false, "", false, false, "", false, null);
    }
    return new CoopIntegrationsDto(
        gateway.isPaystackEnabled(),
        nullToEmpty(gateway.getPaystackPublicKey()),
        notBlank(gateway.getPaystackSecretKey()),
        gateway.isFlutterwaveEnabled(),
        nullToEmpty(gateway.getFlutterwavePublicKey()),
        notBlank(gateway.getFlutterwaveSecretKey()),
        gateway.getUpdatedAt());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
