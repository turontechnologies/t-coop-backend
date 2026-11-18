package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/**
 * What's due next. {@code amount} covers {@code count} installments (1 unless more were asked
 * for); {@code remainingInstallments} is how many are left on the loan, and
 * {@code pendingClaim} is true while a bank-transfer repayment is waiting for the co-op to confirm.
 */
public record NextInstallmentDto(
    int installmentNumber,
    BigDecimal amount,
    int count,
    int remainingInstallments,
    BigDecimal installmentAmount,
    boolean pendingClaim) {}
