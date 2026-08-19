package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;

/**
 * publicKey drives Paystack/Flutterwave's client-side inline widget; checkoutUrl is OPay's
 * server-issued hosted redirect instead (null for the other two gateways, and vice versa).
 */
public record InitializePaymentResponseDto(
    String reference, BigDecimal amount, String gateway, String publicKey, String checkoutUrl) {}
