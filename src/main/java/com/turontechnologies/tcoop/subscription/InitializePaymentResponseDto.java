package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;

public record InitializePaymentResponseDto(
    String reference, BigDecimal amount, String gateway, String publicKey) {}
