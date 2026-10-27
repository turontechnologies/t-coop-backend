package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/** {@code gateway} tells the client which checkout widget {@code publicKey} belongs to —
 * "Paystack" or "Flutterwave" (see CoopGatewayResolver). */
public record InitializeLoanRepaymentResponseDto(
    String reference, int installmentNumber, BigDecimal amount, String gateway, String publicKey) {}
