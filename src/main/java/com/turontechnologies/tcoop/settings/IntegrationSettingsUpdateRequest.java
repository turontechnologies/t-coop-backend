package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.NotNull;

/**
 * Mirrors the frontend's integrationsSchema. These credentials are stored for reference only —
 * the real Paystack integration (src/app/api/paystack/*) always reads its keys from the server
 * environment (PAYSTACK_SECRET_KEY etc.), never from values saved here. Flutterwave has no live
 * route handler at all yet. Don't wire either of these into a live payment call without changing
 * that deliberately — see api-contracts.md § Integrations.
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
