package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One savings transaction — GET /api/v1/cooperatives/{id}/savings (list) and GET
 * /api/v1/savings/{recordId} (single). Matches the frontend's CoopSavingsRecord shape in
 * coop-data.ts, including the joined-in memberName/savingsType display strings so the client
 * never has to look either up separately.
 */
public record SavingsRecordDto(
    String id,
    String memberId,
    String memberName,
    String savingsType,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String method,
    String transactionId,
    LocalDate date,
    String status,
    String receiptUrl) {

  public static SavingsRecordDto from(SavingsRecord record, String memberName, String savingsTypeName) {
    return new SavingsRecordDto(
        record.getId().toString(),
        record.getMemberId(),
        memberName,
        savingsTypeName,
        record.getAmount(),
        record.getBalanceAfter(),
        record.getMethod(),
        record.getTransactionId(),
        record.getRecordDate(),
        record.getStatus(),
        record.getReceiptUrl());
  }
}
