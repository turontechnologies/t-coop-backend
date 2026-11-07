package com.turontechnologies.tcoop.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

class LoanControllerMemberTypeAccessTest {

  @Test
  void memberCanReadActiveLoanTypesFromOwnCooperative() {
    String cooperativeId = "coop-0002";
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    LoanTypeRepository loanTypeRepository = mock(LoanTypeRepository.class);
    LoanRecordRepository loanRecordRepository = mock(LoanRecordRepository.class);
    Member member = mock(Member.class);
    LoanType loanType = mock(LoanType.class);
    LoanType inactiveLoanType = mock(LoanType.class);

    when(authentication.getPrincipal()).thenReturn("MB-0001");
    when(memberRepository.findById("MB-0001")).thenReturn(Optional.of(member));
    when(member.getRole()).thenReturn("member");
    when(member.getCooperativeId()).thenReturn(cooperativeId);
    when(cooperativeRepository.existsById(cooperativeId)).thenReturn(true);
    when(loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId))
        .thenReturn(List.of(loanType, inactiveLoanType));
    when(loanType.getId()).thenReturn(UUID.randomUUID());
    when(loanType.getName()).thenReturn("Household Loan");
    when(loanType.getEligibilityPercent()).thenReturn(BigDecimal.valueOf(200));
    when(loanType.getDurationMonths()).thenReturn(12);
    when(loanType.getMaxAmount()).thenReturn(BigDecimal.valueOf(300_000));
    when(loanType.getRepaymentInterval()).thenReturn("Monthly");
    when(loanType.getNumberOfInstallments()).thenReturn(12);
    when(loanType.getInterestType()).thenReturn("Percentage");
    when(loanType.getInterestAmount()).thenReturn(BigDecimal.valueOf(5));
    when(loanType.getStatus()).thenReturn("Active");
    when(inactiveLoanType.getId()).thenReturn(UUID.randomUUID());
    when(inactiveLoanType.getStatus()).thenReturn("Inactive");

    LoanController controller =
        new LoanController(
            cooperativeRepository,
            memberRepository,
            loanTypeRepository,
            loanRecordRepository,
            mock(AuditLogService.class));

    var response = controller.types(authentication, cooperativeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(List.class);
    assertThat((List<?>) response.getBody()).hasSize(1);
    LoanTypeSummaryDto summary =
        (LoanTypeSummaryDto) ((List<?>) response.getBody()).getFirst();
    assertThat(summary.name()).isEqualTo("Household Loan");
    assertThat(summary.status()).isEqualTo("Active");
    assertThat(summary.earnings()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void memberTypeListIsScopedToTheirCooperativeEvenIfPathNamesAnotherCooperative() {
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    LoanTypeRepository loanTypeRepository = mock(LoanTypeRepository.class);
    Member member = mock(Member.class);

    when(authentication.getPrincipal()).thenReturn("MB-0001");
    when(memberRepository.findById("MB-0001")).thenReturn(Optional.of(member));
    when(member.getRole()).thenReturn("member");
    when(member.getCooperativeId()).thenReturn("coop-0002");
    when(cooperativeRepository.existsById("coop-0002")).thenReturn(true);

    LoanController controller =
        new LoanController(
            cooperativeRepository,
            memberRepository,
            loanTypeRepository,
            mock(LoanRecordRepository.class),
            mock(AuditLogService.class));

    var response = controller.types(authentication, "coop-0003");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<?>) response.getBody()).isEmpty();
    verify(loanTypeRepository)
        .findAllByCooperativeIdOrderByCreatedAtAsc("coop-0002");
    verify(loanTypeRepository, org.mockito.Mockito.never())
        .findAllByCooperativeIdOrderByCreatedAtAsc("coop-0003");
  }

  @Test
  void adminCanReadAllLoanTypesFromOwnCooperative() {
    String cooperativeId = "coop-0002";
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    LoanTypeRepository loanTypeRepository = mock(LoanTypeRepository.class);
    Member admin = mock(Member.class);
    LoanType activeType = mock(LoanType.class);
    LoanType inactiveType = mock(LoanType.class);

    when(authentication.getPrincipal()).thenReturn("COOP-0002");
    when(memberRepository.findById("COOP-0002")).thenReturn(Optional.of(admin));
    when(admin.getRole()).thenReturn("admin");
    when(admin.getCooperativeId()).thenReturn(cooperativeId);
    when(cooperativeRepository.existsById(cooperativeId)).thenReturn(true);
    when(loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId))
        .thenReturn(List.of(activeType, inactiveType));
    when(activeType.getId()).thenReturn(UUID.randomUUID());
    when(activeType.getName()).thenReturn("Active Loan");
    when(activeType.getStatus()).thenReturn("Active");
    when(inactiveType.getId()).thenReturn(UUID.randomUUID());
    when(inactiveType.getName()).thenReturn("Inactive Loan");
    when(inactiveType.getStatus()).thenReturn("Inactive");

    LoanController controller =
        new LoanController(
            cooperativeRepository,
            memberRepository,
            loanTypeRepository,
            mock(LoanRecordRepository.class),
            mock(AuditLogService.class));

    var response = controller.types(authentication, cooperativeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<?>) response.getBody()).hasSize(2);
  }
}
