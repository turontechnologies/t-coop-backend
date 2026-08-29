package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;

public record InitializeSavingsDepositResponseDto(
    String reference,
    BigDecimal amount,
    String publicKey,
    BigDecimal chargeAmount,
    BigDecimal netAmount) {}
