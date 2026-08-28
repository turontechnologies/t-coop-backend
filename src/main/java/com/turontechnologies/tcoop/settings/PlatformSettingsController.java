package com.turontechnologies.tcoop.settings;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs three of the super-admin Settings tabs: Fees & Charges, Payment Settings' Account
 * Details, and Integrations — all read/write the same {@link PlatformSettings} singleton row.
 * Every endpoint here is super-admin only, enforced the same way as {@code AuditLogController}:
 * 403 for anyone else, checked server-side rather than only hidden in the UI.
 */
@RestController
public class PlatformSettingsController {

  private static final Integer SINGLETON_ID = 1;

  private final MemberRepository memberRepository;
  private final PlatformSettingsRepository settingsRepository;
  private final AuditLogService auditLogService;

  public PlatformSettingsController(
      MemberRepository memberRepository,
      PlatformSettingsRepository settingsRepository,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.settingsRepository = settingsRepository;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/settings/fees")
  public ResponseEntity<?> getFees(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    return ResponseEntity.ok(FeeSettingsDto.from(settings()));
  }

  @PatchMapping("/api/v1/settings/fees")
  public ResponseEntity<?> updateFees(
      Authentication authentication,
      @Valid @RequestBody FeeSettingsUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformSettings settings = settings();
    settings.updateFees(
        request.savingsChargeType(),
        request.savingsChargeAmount(),
        request.loansChargeType(),
        request.loansChargeAmount(),
        request.withdrawalFeeAmount(),
        request.withdrawalFeeType());
    settingsRepository.save(settings);

    logSettingsUpdate(authentication, "Fees & Charges", httpRequest);
    return ResponseEntity.ok(FeeSettingsDto.from(settings));
  }

  @GetMapping("/api/v1/settings/collection-account")
  public ResponseEntity<?> getCollectionAccount(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    return ResponseEntity.ok(CollectionAccountDto.from(settings()));
  }

  @PatchMapping("/api/v1/settings/collection-account")
  public ResponseEntity<?> updateCollectionAccount(
      Authentication authentication,
      @Valid @RequestBody CollectionAccountUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformSettings settings = settings();
    settings.updateCollectionAccount(
        request.bankCode(), request.accountNumber(), request.accountName());
    settingsRepository.save(settings);

    logSettingsUpdate(authentication, "Collections Account", httpRequest);
    return ResponseEntity.ok(CollectionAccountDto.from(settings));
  }

  @GetMapping("/api/v1/settings/integrations")
  public ResponseEntity<?> getIntegrations(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    return ResponseEntity.ok(IntegrationSettingsDto.from(settings()));
  }

  @PatchMapping("/api/v1/settings/integrations")
  public ResponseEntity<?> updateIntegrations(
      Authentication authentication,
      @Valid @RequestBody IntegrationSettingsUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformSettings settings = settings();
    settings.updateIntegrations(
        request.paystackEnabled(),
        request.paystackPublicKey(),
        request.paystackSecretKey(),
        request.paystackWebhookSecret(),
        request.flutterwaveEnabled(),
        request.flutterwavePublicKey(),
        request.flutterwaveSecretKey(),
        request.flutterwaveEncryptionKey(),
        request.opayEnabled(),
        request.opayPublicKey(),
        request.opaySecretKey(),
        request.opayMerchantId(),
        request.smsEnabled(),
        request.smsApiKey(),
        request.smsSenderId());
    settingsRepository.save(settings);

    logSettingsUpdate(authentication, "Integrations", httpRequest);
    return ResponseEntity.ok(IntegrationSettingsDto.from(settings));
  }

  @GetMapping("/api/v1/settings/coop-id-format")
  public ResponseEntity<?> getCoopIdFormat(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    return ResponseEntity.ok(CoopIdFormatDto.from(settings()));
  }

  @PatchMapping("/api/v1/settings/coop-id-format")
  public ResponseEntity<?> updateCoopIdFormat(
      Authentication authentication,
      @Valid @RequestBody CoopIdFormatUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformSettings settings = settings();
    settings.updateCoopIdFormat(request.prefix().toUpperCase(), request.padding(), request.type());
    settingsRepository.save(settings);

    logSettingsUpdate(authentication, "Co-op ID Format", httpRequest);
    return ResponseEntity.ok(CoopIdFormatDto.from(settings));
  }

  private PlatformSettings settings() {
    return settingsRepository
        .findById(SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private void logSettingsUpdate(
      Authentication authentication, String resource, HttpServletRequest httpRequest) {
    String callerId = (String) authentication.getPrincipal();
    memberRepository
        .findById(callerId)
        .ifPresent(
            caller ->
                auditLogService.log(
                    caller.getId(),
                    caller.getRole(),
                    "Settings",
                    "Update",
                    resource,
                    "Success",
                    httpRequest));
  }

  /** Returns a 403 ResponseEntity if the caller isn't a super admin, null if they are. */
  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage platform settings"));
    }
    return null;
  }
}
