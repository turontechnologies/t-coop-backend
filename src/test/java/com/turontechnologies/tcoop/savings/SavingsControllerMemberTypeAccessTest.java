package com.turontechnologies.tcoop.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

class SavingsControllerMemberTypeAccessTest {

  @Test
  void memberCanReadSavingsTypesFromOwnCooperative() {
    String cooperativeId = "coop-0002";
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    SavingsTypeRepository savingsTypeRepository = mock(SavingsTypeRepository.class);
    SavingsRecordRepository savingsRecordRepository = mock(SavingsRecordRepository.class);
    Member member = mock(Member.class);
    SavingsType savingsType = mock(SavingsType.class);
    SavingsType inactiveSavingsType = mock(SavingsType.class);

    when(authentication.getPrincipal()).thenReturn("MB-0001");
    when(memberRepository.findById("MB-0001")).thenReturn(Optional.of(member));
    when(member.getRole()).thenReturn("member");
    when(member.getCooperativeId()).thenReturn(cooperativeId);
    when(cooperativeRepository.existsById(cooperativeId)).thenReturn(true);
    when(savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId))
        .thenReturn(List.of(savingsType, inactiveSavingsType));
    when(savingsType.getId()).thenReturn(java.util.UUID.randomUUID());
    when(savingsType.getName()).thenReturn("Test Savings");
    when(savingsType.getMinAmount()).thenReturn(BigDecimal.valueOf(500));
    when(savingsType.getMaxAmount()).thenReturn(BigDecimal.valueOf(10_000));
    when(savingsType.getStatus()).thenReturn("Active");
    when(inactiveSavingsType.getId()).thenReturn(java.util.UUID.randomUUID());
    when(inactiveSavingsType.getStatus()).thenReturn("Inactive");

    SavingsController controller =
        new SavingsController(
            cooperativeRepository,
            memberRepository,
            savingsTypeRepository,
            savingsRecordRepository,
            mock(AuditLogService.class),
            mock(CoopPermissions.class));

    var response = controller.types(authentication, cooperativeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(List.class);
    assertThat((List<?>) response.getBody()).hasSize(1);
    SavingsTypeSummaryDto summary =
        (SavingsTypeSummaryDto) ((List<?>) response.getBody()).getFirst();
    assertThat(summary.name()).isEqualTo("Test Savings");
    assertThat(summary.status()).isEqualTo("Active");
    assertThat(summary.total()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void memberTypeListIsScopedToTheirCooperativeEvenIfPathNamesAnotherCooperative() {
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    SavingsTypeRepository savingsTypeRepository = mock(SavingsTypeRepository.class);
    Member member = mock(Member.class);

    when(authentication.getPrincipal()).thenReturn("MB-0001");
    when(memberRepository.findById("MB-0001")).thenReturn(Optional.of(member));
    when(member.getRole()).thenReturn("member");
    when(member.getCooperativeId()).thenReturn("coop-0002");
    when(cooperativeRepository.existsById("coop-0002")).thenReturn(true);

    SavingsController controller =
        new SavingsController(
            cooperativeRepository,
            memberRepository,
            savingsTypeRepository,
            mock(SavingsRecordRepository.class),
            mock(AuditLogService.class),
            mock(CoopPermissions.class));

    var response = controller.types(authentication, "coop-0003");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<?>) response.getBody()).isEmpty();
    verify(savingsTypeRepository)
        .findAllByCooperativeIdOrderByCreatedAtAsc("coop-0002");
    verify(savingsTypeRepository, org.mockito.Mockito.never())
        .findAllByCooperativeIdOrderByCreatedAtAsc("coop-0003");
  }

  @Test
  void adminCanReadAllSavingsTypesFromOwnCooperative() {
    String cooperativeId = "coop-0002";
    Authentication authentication = mock(Authentication.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    SavingsTypeRepository savingsTypeRepository = mock(SavingsTypeRepository.class);
    Member admin = mock(Member.class);
    SavingsType activeType = mock(SavingsType.class);
    SavingsType inactiveType = mock(SavingsType.class);

    when(authentication.getPrincipal()).thenReturn("COOP-0002");
    when(memberRepository.findById("COOP-0002")).thenReturn(Optional.of(admin));
    when(admin.getRole()).thenReturn("admin");
    when(admin.getCooperativeId()).thenReturn(cooperativeId);
    CoopPermissions coopPermissions = mock(CoopPermissions.class);
    when(coopPermissions.isStaff(admin, cooperativeId)).thenReturn(true);
    when(coopPermissions.allows(
            admin, cooperativeId, "Savings & Contributions", null, false))
        .thenReturn(true);
    when(cooperativeRepository.existsById(cooperativeId)).thenReturn(true);
    when(savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId))
        .thenReturn(List.of(activeType, inactiveType));
    when(activeType.getId()).thenReturn(java.util.UUID.randomUUID());
    when(activeType.getName()).thenReturn("Active Savings");
    when(activeType.getStatus()).thenReturn("Active");
    when(inactiveType.getId()).thenReturn(java.util.UUID.randomUUID());
    when(inactiveType.getName()).thenReturn("Inactive Savings");
    when(inactiveType.getStatus()).thenReturn("Inactive");

    SavingsController controller =
        new SavingsController(
            cooperativeRepository,
            memberRepository,
            savingsTypeRepository,
            mock(SavingsRecordRepository.class),
            mock(AuditLogService.class),
            coopPermissions);

    var response = controller.types(authentication, cooperativeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<?>) response.getBody()).hasSize(2);
  }
}
