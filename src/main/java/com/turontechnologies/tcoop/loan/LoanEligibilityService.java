package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * The one place that decides how much a member can borrow, so the dashboard's "Loan Eligibility"
 * card, the loan form and the application check always agree.
 *
 * <ul>
 *   <li>Savings are successful savings only (what's actually in the account).
 *   <li>Times two: up to {@code savingsMultiplier} × savings; Within: up to {@code withinPercent}%.
 *   <li>Minus what's already tied up in that mode: the principal still owed on running loans, and
 *       the amount of loans still being decided or paid out. (A running Within loan has already
 *       come out of savings, so only Within loans not yet paid out count against Within.)
 *   <li>Capped by each loan type's own maximum, never below zero.
 *   <li>Zero everywhere when a co-op rule rules the member out (membership length, minimum
 *       savings, one loan at a time), with the reason.
 * </ul>
 */
@Service
public class LoanEligibilityService {

  public record TypeEligibility(LoanType type, BigDecimal timesTwo, BigDecimal within) {
    public BigDecimal best() {
      return timesTwo.max(within);
    }
  }

  public record Result(
      BigDecimal savings,
      /** Why the member can't borrow at all right now, or null. */
      String problem,
      BigDecimal tiedTimesTwo,
      BigDecimal tiedWithin,
      List<TypeEligibility> perType) {

    /** The most the member can borrow on any active loan type, in either mode. */
    public BigDecimal best() {
      return perType.stream().map(TypeEligibility::best).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public BigDecimal limit(LoanType type, String mode) {
      return perType.stream()
          .filter(t -> t.type().getId().equals(type.getId()))
          .findFirst()
          .map(t -> LoanEligibility.WITHIN.equals(mode) ? t.within() : t.timesTwo())
          .orElse(BigDecimal.ZERO);
    }
  }

  private final SavingsRecordRepository savingsRecordRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanTypeRepository loanTypeRepository;

  public LoanEligibilityService(
      SavingsRecordRepository savingsRecordRepository,
      LoanRecordRepository loanRecordRepository,
      LoanTypeRepository loanTypeRepository) {
    this.savingsRecordRepository = savingsRecordRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanTypeRepository = loanTypeRepository;
  }

  public Result evaluate(Cooperative coop, Member member) {
    BigDecimal savings = savingsRecordRepository.sumByMember(member.getId()).max(BigDecimal.ZERO);
    List<LoanRecord> loans =
        loanRecordRepository.findAllByCooperativeIdAndMemberIdOrderByCreatedAtDesc(coop.getId(), member.getId());

    BigDecimal tiedTimesTwo = BigDecimal.ZERO;
    BigDecimal tiedWithin = BigDecimal.ZERO;
    for (LoanRecord loan : loans) {
      boolean inProgress = LoanSelfServiceController.IN_PROGRESS.contains(loan.getStatus());
      boolean running = "Active".equals(loan.getStatus());
      if (loan.isWithin()) {
        if (inProgress) tiedWithin = tiedWithin.add(loan.getAmount());
      } else if (inProgress) {
        tiedTimesTwo = tiedTimesTwo.add(loan.getAmount());
      } else if (running) {
        tiedTimesTwo = tiedTimesTwo.add(LoanBalances.principalOwed(loan));
      }
    }

    String problem = problemFor(coop, member, savings, loans);
    final BigDecimal t2 = tiedTimesTwo;
    final BigDecimal tw = tiedWithin;
    List<TypeEligibility> perType =
        loanTypeRepository.findAllByCooperativeId(coop.getId()).stream()
            .filter(type -> "Active".equals(type.getStatus()))
            .map(
                type -> {
                  if (problem != null) return new TypeEligibility(type, BigDecimal.ZERO, BigDecimal.ZERO);
                  BigDecimal timesTwo =
                      capped(savings.multiply(coop.getLoanSavingsMultiplier()).subtract(t2), type);
                  BigDecimal within =
                      capped(
                          savings.multiply(coop.getLoanWithinPercent())
                              .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)
                              .subtract(tw),
                          type);
                  return new TypeEligibility(type, timesTwo, within);
                })
            .toList();
    return new Result(savings, problem, t2, tw, perType);
  }

  private static BigDecimal capped(BigDecimal amount, LoanType type) {
    return amount.max(BigDecimal.ZERO).min(type.getMaxAmount()).setScale(2, RoundingMode.DOWN);
  }

  /** Null when the member meets the co-op's loan rules, else why not. */
  private static String problemFor(Cooperative coop, Member member, BigDecimal savings, List<LoanRecord> loans) {
    int minMonths = coop.getLoanMinMembershipMonths();
    if (minMonths > 0 && member.getCreatedAt() != null) {
      long monthsIn = ChronoUnit.MONTHS.between(member.getCreatedAt(), LocalDateTime.now());
      if (monthsIn < minMonths) {
        return "You can apply for a loan once you've been a member for " + minMonths + " month"
            + (minMonths == 1 ? "" : "s") + ".";
      }
    }
    if (coop.getLoanMinSavings().signum() > 0 && savings.compareTo(coop.getLoanMinSavings()) < 0) {
      return "You need at least " + naira(coop.getLoanMinSavings()) + " in savings to apply for a loan.";
    }
    if (!coop.isLoanAllowConcurrent()) {
      boolean busy =
          loans.stream()
              .anyMatch(loan -> "Active".equals(loan.getStatus())
                  || LoanSelfServiceController.IN_PROGRESS.contains(loan.getStatus()));
      if (busy) return "You already have a loan running or in progress. Apply again once it's repaid.";
    }
    return null;
  }

  static String naira(BigDecimal amount) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    return "₦" + format.format(amount);
  }
}
