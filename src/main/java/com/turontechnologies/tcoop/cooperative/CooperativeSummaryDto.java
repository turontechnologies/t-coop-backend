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
    long memberCount,
    BigDecimal totalSavings,
    BigDecimal totalLoans) {}
