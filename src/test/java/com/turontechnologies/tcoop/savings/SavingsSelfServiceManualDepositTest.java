package com.turontechnologies.tcoop.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

class SavingsSelfServiceManualDepositTest {

  @Test
  void manualDepositRejectsInactiveSavingsType() {
    String cooperativeId = "coop-0002";
    UUID savingsTypeId = UUID.randomUUID();
    Authentication authentication = mock(Authentication.class);
    CooperativeRepository cooperativeRepository = mock(CooperativeRepository.class);
    MemberRepository memberRepository = mock(MemberRepository.class);
    SavingsTypeRepository savingsTypeRepository = mock(SavingsTypeRepository.class);
    SavingsRecordRepository savingsRecordRepository = mock(SavingsRecordRepository.class);
    Member admin = mock(Member.class);
    Member target = mock(Member.class);
    SavingsType inactiveType = mock(SavingsType.class);

    when(authentication.getPrincipal()).thenReturn("COOP-0002");
    when(memberRepository.findById("COOP-0002")).thenReturn(Optional.of(admin));
    when(admin.getRole()).thenReturn("admin");
    when(admin.getCooperativeId()).thenReturn(cooperativeId);
    when(memberRepository.findById("COOP-0002")).thenReturn(Optional.of(admin));
    when(memberRepository.findById("MB-0001")).thenReturn(Optional.of(target));
    when(target.getCooperativeId()).thenReturn(cooperativeId);
    when(savingsTypeRepository.findById(savingsTypeId)).thenReturn(Optional.of(inactiveType));
    when(inactiveType.getCooperativeId()).thenReturn(cooperativeId);
    when(inactiveType.getStatus()).thenReturn("Inactive");

    SavingsSelfServiceController controller =
        new SavingsSelfServiceController(
            cooperativeRepository,
            memberRepository,
            savingsTypeRepository,
            savingsRecordRepository,
            mock(SavingsPaymentIntentRepository.class),
            mock(SavingsRequestRepository.class),
            mock(PlatformSettingsRepository.class),
            mock(CoopGatewayResolver.class),
            mock(PaymentGatewayService.class),
            mock(AuditLogService.class));

    var response =
        controller.manualDeposit(
            authentication,
            cooperativeId,
            new ManualSavingsDepositRequest(
                "MB-0001", savingsTypeId.toString(), BigDecimal.valueOf(100), null),
            mock(jakarta.servlet.http.HttpServletRequest.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(savingsRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(cooperativeRepository, never()).findById(cooperativeId);
  }
}
