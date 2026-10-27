package com.turontechnologies.tcoop.coopgateway;

import jakarta.validation.constraints.NotNull;

/**
 * A blank (or null) secret key means "keep the one already saved" — the browser never has the
 * saved secret to send back (see CoopIntegrationsDto), so it only ever sends a new one.
 */
public record CoopIntegrationsUpdateRequest(
    @NotNull(message = "paystackEnabled is required") Boolean paystackEnabled,
    String paystackPublicKey,
    String paystackSecretKey,
    @NotNull(message = "flutterwaveEnabled is required") Boolean flutterwaveEnabled,
    String flutterwavePublicKey,
    String flutterwaveSecretKey) {}
