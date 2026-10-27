package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;

/** {@code gateway} tells the client which checkout widget {@code publicKey} belongs to —
 * "Paystack" or "Flutterwave" (see CoopGatewayResolver). */
public record InitializeSavingsDepositResponseDto(
    String reference,
    BigDecimal amount,
    String gateway,
    String publicKey,
    BigDecimal chargeAmount,
    BigDecimal netAmount) {}
