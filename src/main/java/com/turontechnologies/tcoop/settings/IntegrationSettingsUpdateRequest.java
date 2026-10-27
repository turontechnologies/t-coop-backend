package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.NotNull;

/**
 * Mirrors the frontend's integrationsSchema. Every credential here is read live — subscription
 * checkout, bank lookups, and co-op payments that fall back to the platform's gateway (see
 * api-contracts.md § 13 for exactly where each is used).
 */
public record IntegrationSettingsUpdateRequest(
    @NotNull(message = "paystackEnabled is required") Boolean paystackEnabled,
    String paystackPublicKey,
    String paystackSecretKey,
    String paystackWebhookSecret,
    @NotNull(message = "flutterwaveEnabled is required") Boolean flutterwaveEnabled,
    String flutterwavePublicKey,
    String flutterwaveSecretKey,
    String flutterwaveEncryptionKey,
    @NotNull(message = "opayEnabled is required") Boolean opayEnabled,
    String opayPublicKey,
    String opaySecretKey,
    String opayMerchantId,
    @NotNull(message = "smsEnabled is required") Boolean smsEnabled,
    String smsApiKey,
    String smsSenderId) {}
