package com.turontechnologies.tcoop.settings;

import java.math.BigDecimal;

/**
 * The platform defaults a newly created co-op starts with (each co-op's own platform fees are
 * then edited per co-op), plus the subscription grace period. Matches the frontend's FeeSettings
 * shape (src/lib/settings-data.ts).
 */
public record FeeSettingsDto(
    String savingsChargeType,
    BigDecimal savingsChargeAmount,
    String loansChargeType,
    BigDecimal loansChargeAmount,
    BigDecimal withdrawalFeeAmount,
    String withdrawalFeeType,
    String loanApplicationFeeType,
    BigDecimal loanApplicationFeeAmount,
    int subscriptionGraceDays) {

  public static FeeSettingsDto from(PlatformSettings settings) {
    return new FeeSettingsDto(
        settings.getSavingsChargeType(),
        settings.getSavingsChargeAmount(),
        settings.getLoansChargeType(),
        settings.getLoansChargeAmount(),
        settings.getWithdrawalFeeAmount(),
        settings.getWithdrawalFeeType(),
        settings.getLoanApplicationFeeType(),
        settings.getLoanApplicationFeeAmount(),
        settings.getSubscriptionGraceDays());
  }
}
