package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * How much the application fee is for a loan, and which paid fee pays for it. The admin chooses
 * one fee for every loan ("General") or each loan type's own fee ("PerType"). A paid fee pays for
 * exactly one application; with per-type fees it only pays for the type it was paid for.
 */
@Service
public class LoanFeeRules {

  private final LoanApplicationFeeRepository feeRepository;

  public LoanFeeRules(LoanApplicationFeeRepository feeRepository) {
    this.feeRepository = feeRepository;
  }

  /** The fee for applying for this loan type (0 = no fee). */
  public static BigDecimal feeFor(Cooperative coop, LoanType type) {
    if (coop.isLoanFeePerType()) return type == null ? BigDecimal.ZERO : type.getApplicationFee();
    return coop.getLoanApplicationFee();
  }

  /** A paid, unused fee that pays for an application of this type, or null. */
  public LoanApplicationFee creditFor(Cooperative coop, String memberId, UUID loanTypeId) {
    var paid =
        feeRepository.findAllByCooperativeIdAndMemberIdAndStatusOrderByPaidAtAsc(
            coop.getId(), memberId, LoanApplicationFee.PAID);
    if (!coop.isLoanFeePerType()) return paid.isEmpty() ? null : paid.get(0);
    return paid.stream().filter(f -> Objects.equals(f.getLoanTypeId(), loanTypeId)).findFirst().orElse(null);
  }
}
