package com.turontechnologies.tcoop.settings;

import java.math.BigDecimal;

/** Matches the frontend's FeeSettings shape (src/lib/settings-data.ts). */
public record FeeSettingsDto(
    String savingsChargeType,
    BigDecimal savingsChargeAmount,
    String loansChargeType,
    BigDecimal loansChargeAmount,
    BigDecimal withdrawalFeePercent) {

  public static FeeSettingsDto from(PlatformSettings settings) {
    return new FeeSettingsDto(
        settings.getSavingsChargeType(),
        settings.getSavingsChargeAmount(),
        settings.getLoansChargeType(),
        settings.getLoansChargeAmount(),
        settings.getWithdrawalFeePercent());
  }
}
