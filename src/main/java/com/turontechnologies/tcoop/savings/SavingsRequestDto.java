package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A savings withdrawal (or, potentially, deposit) request awaiting or past admin decision. */
public record SavingsRequestDto(
    String id,
    String memberId,
    String memberName,
    String requestType,
    String savingsType,
    BigDecimal amount,
    String note,
    String status,
    BigDecimal feePercent,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    LocalDateTime requestedAt,
    LocalDateTime resolvedAt,
    String declineReason,
    /** How an approved withdrawal was paid: Online, Transfer, Cheque or Cash. */
    String payoutMethod,
    String payoutReference,
    String payoutReceiptUrl) {

  public static SavingsRequestDto from(SavingsRequest request, String memberName, String savingsTypeName) {
    return new SavingsRequestDto(
        request.getId().toString(),
        request.getMemberId(),
        memberName,
        request.getRequestType(),
        savingsTypeName,
        request.getAmount(),
        request.getNote(),
        request.getStatus(),
        request.getFeePercent(),
        request.getFeeAmount(),
        request.getNetAmount(),
        request.getRequestedAt(),
        request.getResolvedAt(),
        request.getDeclineReason(),
        request.getPayoutMethod(),
        request.getPayoutReference(),
        request.getPayoutReceiptUrl());
  }
}
