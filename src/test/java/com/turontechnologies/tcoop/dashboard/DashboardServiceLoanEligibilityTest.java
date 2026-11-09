package com.turontechnologies.tcoop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanType;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceLoanEligibilityTest {

  @Test
  void memberSummaryIncludesEligibilityForEachActiveLoanTypeAndMode() {
    String cooperativeId = "coop-0002";
    String memberId = "MB-0001";
    LoanType smallLoan = loanType(cooperativeId, "Small Loan", "1000");
    LoanType largeLoan = loanType(cooperativeId, "Large Loan", "25000");
    LoanType inactiveLoan = loanType(cooperativeId, "Inactive Loan", "25000");
    inactiveLoan.setStatus("Inactive");

    LoanTypeRepository loanTypeRepository = mock(LoanTypeRepository.class);
    SavingsRecordRepository savingsRecordRepository = mock(SavingsRecordRepository.class);
    LoanRecordRepository loanRecordRepository = mock(LoanRecordRepository.class);
    when(loanTypeRepository.findAllByCooperativeId(cooperativeId))
        .thenReturn(List.of(smallLoan, largeLoan, inactiveLoan));
    when(savingsRecordRepository.sumByMember(memberId)).thenReturn(new BigDecimal("10000"));
    when(loanRecordRepository.sumByMember(memberId)).thenReturn(BigDecimal.ZERO);
    when(savingsRecordRepository.findAllByMemberIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(memberId),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    when(loanRecordRepository.findAllByMemberIdOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(memberId),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    SavingsTypeRepository savingsTypeRepository = mock(SavingsTypeRepository.class);
    when(savingsTypeRepository.findAllByCooperativeId(cooperativeId)).thenReturn(List.of());

    DashboardService service =
        new DashboardService(
            mock(MemberRepository.class),
            mock(CooperativeRepository.class),
            savingsRecordRepository,
            savingsTypeRepository,
            loanRecordRepository,
            loanTypeRepository,
            mock(com.turontechnologies.tcoop.coopstaff.CoopPermissions.class));
    Member member =
        new Member(memberId, cooperativeId, "member", "hash", "First", "Last", "member@example.com");

    DashboardSummaryDto summary = service.getSummary(member);

    assertThat(summary.loanEligibilityByType())
        .extracting(LoanTypeEligibilityDto::loanTypeName)
        .containsExactly("Small Loan", "Large Loan");
    assertThat(summary.loanEligibilityByType().get(0).timesTwo())
        .isEqualByComparingTo("1000");
    assertThat(summary.loanEligibilityByType().get(0).within())
        .isEqualByComparingTo("1000");
    // ₦10,000 saved: Times two allows up to twice that, Within up to the savings themselves.
    assertThat(summary.loanEligibilityByType().get(1).timesTwo())
        .isEqualByComparingTo("20000");
    assertThat(summary.loanEligibilityByType().get(1).within())
        .isEqualByComparingTo("10000");
    assertThat(summary.cards().get(3).value()).isEqualByComparingTo("20000");
  }

  private static LoanType loanType(String cooperativeId, String name, String maxAmount) {
    return new LoanType(
        cooperativeId,
        name,
        BigDecimal.valueOf(100),
        12,
        new BigDecimal(maxAmount),
        "Monthly",
        12,
        "NoInterest",
        BigDecimal.ZERO);
  }
}
