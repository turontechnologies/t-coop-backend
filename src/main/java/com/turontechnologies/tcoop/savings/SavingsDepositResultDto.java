package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;

/** Wraps the created {@link SavingsRecordDto} with the gross-vs-net breakdown for the receipt —
 * {@code record.amount} is already net (what actually got credited to the member's balance);
 * {@code grossAmount} is what the member actually paid in, and {@code chargeAmount} is the
 * combined co-op + platform savings charge taken out of it. */
public record SavingsDepositResultDto(
    SavingsRecordDto record, BigDecimal grossAmount, BigDecimal chargeAmount) {}
