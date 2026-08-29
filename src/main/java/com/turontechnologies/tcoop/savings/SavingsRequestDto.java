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
    LocalDateTime resolvedAt) {

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
        request.getResolvedAt());
  }
}
