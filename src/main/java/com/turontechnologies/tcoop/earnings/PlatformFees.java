package com.turontechnologies.tcoop.earnings;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The platform's cut from a co-operative, worked out from that co-op's own platform fees (set by
 * the super admin per co-op), and the place every earned fee is recorded.
 *
 * <p>Payments go through the co-op's own gateway, so this ledger is the record of what each co-op
 * owes the platform; it does not move money itself.
 */
@Service
public class PlatformFees {

  private final PlatformEarningRepository earningRepository;

  public PlatformFees(PlatformEarningRepository earningRepository) {
    this.earningRepository = earningRepository;
  }

  /** A "Fixed" fee is the amount itself; "Percentage" is that percent of {@code base}. */
  public static BigDecimal feeFor(String type, BigDecimal feeAmount, BigDecimal base) {
    if (feeAmount == null || base == null) return BigDecimal.ZERO;
    if ("Fixed".equals(type)) return feeAmount.setScale(2, RoundingMode.HALF_UP);
    return base.multiply(feeAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  public static BigDecimal savingsCharge(Cooperative coop, BigDecimal deposit) {
    return feeFor(coop.getPlatformSavingsChargeType(), coop.getPlatformSavingsChargeAmount(), deposit);
  }

  public static BigDecimal loansCharge(Cooperative coop, BigDecimal loan) {
    return feeFor(coop.getPlatformLoansChargeType(), coop.getPlatformLoansChargeAmount(), loan);
  }

  public static BigDecimal withdrawalFee(Cooperative coop, BigDecimal withdrawal) {
    return feeFor(coop.getPlatformWithdrawalFeeType(), coop.getPlatformWithdrawalFeeAmount(), withdrawal);
  }

  public static BigDecimal loanApplicationFeeShare(Cooperative coop, BigDecimal applicationFee) {
    return feeFor("Percentage", coop.getPlatformLoanFeePercent(), applicationFee);
  }

  /**
   * Records a fee the platform earned. Nothing is recorded for a zero fee, and a second call for
   * the same source and reference is ignored, so a retried payment can't count twice.
   */
  public void record(
      String cooperativeId,
      String memberId,
      String source,
      BigDecimal baseAmount,
      BigDecimal amount,
      String reference) {
    if (amount == null || amount.signum() <= 0 || reference == null) return;
    if (earningRepository.existsBySourceAndReference(source, reference)) return;
    try {
      earningRepository.save(
          new PlatformEarning(cooperativeId, memberId, source, baseAmount, amount, reference));
    } catch (DataIntegrityViolationException alreadyRecorded) {
      // Two requests raced on the same reference; the unique key kept one.
    }
  }
}
