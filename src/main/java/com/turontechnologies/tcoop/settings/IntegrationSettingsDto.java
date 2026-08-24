package com.turontechnologies.tcoop.settings;

/** Matches the frontend's IntegrationSettings shape (src/lib/settings-data.ts). */
public record IntegrationSettingsDto(
    boolean paystackEnabled,
    String paystackPublicKey,
    String paystackSecretKey,
    String paystackWebhookSecret,
    boolean flutterwaveEnabled,
    String flutterwavePublicKey,
    String flutterwaveSecretKey,
    String flutterwaveEncryptionKey,
    boolean opayEnabled,
    String opayPublicKey,
    String opaySecretKey,
    String opayMerchantId,
    boolean smsEnabled,
    String smsApiKey,
    String smsSenderId) {

  public static IntegrationSettingsDto from(PlatformSettings settings) {
    return new IntegrationSettingsDto(
        settings.isPaystackEnabled(),
        settings.getPaystackPublicKey(),
        settings.getPaystackSecretKey(),
        settings.getPaystackWebhookSecret(),
        settings.isFlutterwaveEnabled(),
        settings.getFlutterwavePublicKey(),
        settings.getFlutterwaveSecretKey(),
        settings.getFlutterwaveEncryptionKey(),
        settings.isOpayEnabled(),
        settings.getOpayPublicKey(),
        settings.getOpaySecretKey(),
        settings.getOpayMerchantId(),
        settings.isSmsEnabled(),
        settings.getSmsApiKey(),
        settings.getSmsSenderId());
  }
}
