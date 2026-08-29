package com.turontechnologies.tcoop.settings;

import java.math.BigDecimal;

/** GET /api/v1/settings/withdrawal-fee — just the platform's own withdrawal fee, readable by any
 * authenticated member (not just super admin, unlike {@code /settings/fees}). A member submitting
 * a withdrawal needs to see the real platform-side fee alongside their co-op's own; the full
 * PlatformSettings row carries payment-gateway secret keys they have no business seeing. */
public record WithdrawalFeeDto(BigDecimal withdrawalFeeAmount, String withdrawalFeeType) {

  public static WithdrawalFeeDto from(PlatformSettings settings) {
    return new WithdrawalFeeDto(settings.getWithdrawalFeeAmount(), settings.getWithdrawalFeeType());
  }
}
