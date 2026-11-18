package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/**
 * Where a loan stands, for the repay screens. {@code installmentAmount} finishes the current
 * installment (less than a full month when part of it is already paid); {@code amount} covers
 * {@code count} installments. {@code monthlyRepayment}, {@code amountRepaid},
 * {@code outstanding} and {@code totalRepayment} let a screen show how much of the schedule any
 * typed amount pays (half a month, a month and a half, …).
 */
public record NextInstallmentDto(
    int installmentNumber,
    BigDecimal amount,
    int count,
    int remainingInstallments,
    BigDecimal installmentAmount,
    boolean pendingClaim,
    BigDecimal monthlyRepayment,
    BigDecimal amountRepaid,
    BigDecimal outstanding,
    BigDecimal totalRepayment,
    int numberOfRepayments) {}
