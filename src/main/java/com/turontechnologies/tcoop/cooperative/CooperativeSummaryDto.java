package com.turontechnologies.tcoop.cooperative;

import java.math.BigDecimal;

/** Matches api-contracts.md §2 — one list item, or the single-co-op GET response. */
public record CooperativeSummaryDto(
    String id,
    String name,
    String adminName,
    String contactEmail,
    String contactPhone,
    String address,
    String country,
    String state,
    String city,
    String status,
    String currency,
    BigDecimal withdrawalFeeAmount,
    String withdrawalFeeType,
    String bankCode,
    String accountNumber,
    String accountName,
    String logoUrl,
    String memberIdPrefix,
    int memberIdPadding,
    String memberIdType,
    int minGuarantors,
    long memberCount,
    long savingsTypeCount,
    long loanTypeCount,
    BigDecimal totalSavings,
    BigDecimal totalLoans) {}
