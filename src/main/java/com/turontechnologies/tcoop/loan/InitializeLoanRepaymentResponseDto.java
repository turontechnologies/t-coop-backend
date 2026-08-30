package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

public record InitializeLoanRepaymentResponseDto(
    String reference, int installmentNumber, BigDecimal amount, String publicKey) {}
