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
    boolean requireMemberGuarantors,
    String memberGuarantorSource,
    int memberGuarantorsFromCoop,
    int memberGuarantorsFromOutside,
    long memberCount,
    long savingsTypeCount,
    long loanTypeCount,
    BigDecimal totalSavings,
    BigDecimal totalLoans,
    BigDecimal savingsChargeAmount,
    String savingsChargeType,
    BigDecimal loansChargeAmount,
    String loansChargeType,
    /** What the co-op calls the alternative ID. */
    String alternativeIdLabel) {}
