package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.cooperative.MemberIdGenerator;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsType;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The co-op's side of new-member applications: the joining rules and fee (Settings), and the list
 * of applications with their checks, to approve or reject. Settings need Settings access;
 * applications need Members Directory access (to read, or to write for a decision).
 */
@RestController
public class MembershipAdminController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final RegistrationSettingsRepository settingsRepository;
  private final MembershipApplicationRepository applicationRepository;
  private final ApplicationGuarantorRepository guarantorRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final MembershipApplicationService service;
  private final ApplicationChecks applicationChecks;
  private final MemberIdGenerator memberIdGenerator;
  private final CoopPermissions coopPermissions;
  private final AuditLogService auditLogService;

  public MembershipAdminController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      RegistrationSettingsRepository settingsRepository,
      MembershipApplicationRepository applicationRepository,
      ApplicationGuarantorRepository guarantorRepository,
      SavingsTypeRepository savingsTypeRepository,
      MembershipApplicationService service,
      ApplicationChecks applicationChecks,
      MemberIdGenerator memberIdGenerator,
      CoopPermissions coopPermissions,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.settingsRepository = settingsRepository;
    this.applicationRepository = applicationRepository;
    this.guarantorRepository = guarantorRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.service = service;
    this.applicationChecks = applicationChecks;
    this.memberIdGenerator = memberIdGenerator;
    this.coopPermissions = coopPermissions;
    this.auditLogService = auditLogService;
  }

  public record SettingsDto(
      boolean open,
      BigDecimal formFee,
      BigDecimal firstSavings,
      BigDecimal totalFee,
      String savingsTypeId,
      String rules,
      String requirements,
      List<String> defaultRequirements,
      boolean requireIdDocument,
      int minAge,
      boolean canPayOnline,
      long waitingCount) {}

  public record SettingsRequest(
      boolean open,
      @NotNull(message = "Enter the form fee (0 if there is none)") @DecimalMin(value = "0", message = "The form fee can't be less than 0")
          @DecimalMax(value = "100000000", message = "The form fee is too big") BigDecimal formFee,
      @NotNull(message = "Enter the first savings (0 if there is none)") @DecimalMin(value = "0", message = "The first savings can't be less than 0")
          @DecimalMax(value = "100000000", message = "The first savings is too big") BigDecimal firstSavings,
      String savingsTypeId,
      @Size(max = 20000, message = "Keep the rules under 20,000 letters") String rules,
      @Size(max = 5000, message = "Keep the list under 5,000 letters") String requirements,
      boolean requireIdDocument,
      @Min(value = 16, message = "The youngest age can't be under 16") @Max(value = 100, message = "That age is too high") int minAge) {}

  public record ApproveRequest(@Size(max = 20) String membershipId, @Size(max = 1000) String note) {}

  public record RejectRequest(
      @NotBlank(message = "Tell them why, so they know what to fix") @Size(max = 1000, message = "Keep the reason under 1,000 letters") String reason) {}

  @ExceptionHandler(MembershipApplicationService.ApplicationProblem.class)
  public ResponseEntity<?> problem(MembershipApplicationService.ApplicationProblem e) {
    return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
  }

  @GetMapping("/api/v1/cooperatives/{id}/registration-settings")
  public ResponseEntity<?> settings(Authentication authentication, @PathVariable String id) {
    requireAccess(authentication, id, "Settings", false);
    findCoop(id);
    return ResponseEntity.ok(toDto(service.settingsFor(id)));
  }

  @PutMapping("/api/v1/cooperatives/{id}/registration-settings")
  public ResponseEntity<?> updateSettings(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SettingsRequest request,
      HttpServletRequest httpRequest) {
    Member caller = requireAccess(authentication, id, "Settings", true);
    findCoop(id);
    UUID savingsTypeId = null;
    if (request.savingsTypeId() != null && !request.savingsTypeId().isBlank()) {
      try {
        savingsTypeId = UUID.fromString(request.savingsTypeId());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Choose a savings type from the list."));
      }
      SavingsType type = savingsTypeRepository.findById(savingsTypeId).orElse(null);
      if (type == null || !id.equals(type.getCooperativeId())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Choose a savings type from the list."));
      }
    }
    if (request.firstSavings().signum() > 0 && savingsTypeId == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Choose which savings the first savings goes into.", "field", "savingsTypeId"));
    }
    BigDecimal total = request.formFee().add(request.firstSavings());
    if (request.open() && total.signum() > 0 && !service.canPayOnline(id)) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "To charge a joining fee, first connect Paystack or Flutterwave in Settings → Integrations. "
              + "Or set both amounts to 0."));
    }
    RegistrationSettings settings = settingsRepository.findById(id).orElseGet(() -> new RegistrationSettings(id));
    settings.update(
        request.open(),
        request.formFee(),
        request.firstSavings(),
        savingsTypeId,
        request.rules(),
        request.requirements(),
        request.requireIdDocument(),
        request.minAge());
    settingsRepository.save(settings);
    auditLogService.log(caller.getId(), caller.getRole(), "Settings", "Update", "New member applications", "Success", httpRequest);
    return ResponseEntity.ok(toDto(settings));
  }

  /** {@code status}: Submitted (waiting, the default), Approved, Rejected or All. */
  @GetMapping("/api/v1/cooperatives/{id}/membership-applications")
  public ResponseEntity<?> list(
      Authentication authentication, @PathVariable String id, @RequestParam(required = false) String status) {
    requireAccess(authentication, id, "Members Directory", false);
    Cooperative coop = findCoop(id);
    List<String> statuses =
        switch (status == null ? "Submitted" : status) {
          case "Approved" -> List.of(MembershipApplication.APPROVED);
          case "Rejected" -> List.of(MembershipApplication.REJECTED);
          case "All" -> List.of(MembershipApplication.SUBMITTED, MembershipApplication.APPROVED, MembershipApplication.REJECTED);
          default -> List.of(MembershipApplication.SUBMITTED);
        };
    List<MembershipApplication> apps =
        applicationRepository.findAllByCooperativeIdAndStatusInOrderBySubmittedAtDesc(id, statuses);
    Map<UUID, List<ApplicationGuarantor>> guarantors =
        apps.isEmpty()
            ? Map.of()
            : guarantorRepository.findAllByApplicationIdIn(apps.stream().map(MembershipApplication::getId).toList()).stream()
                .collect(Collectors.groupingBy(ApplicationGuarantor::getApplicationId));
    RegistrationSettings settings = service.settingsFor(id);
    List<ApplicationViews.AdminListItem> items =
        apps.stream()
            .map(app -> {
              List<ApplicationGuarantor> own = guarantors.getOrDefault(app.getId(), List.of());
              ApplicationChecks.Result checks = applicationChecks.run(app, coop, settings, own);
              List<ApplicationGuarantor> asked = own.stream().filter(ApplicationGuarantor::isMember).toList();
              return new ApplicationViews.AdminListItem(
                  app.getId(), app.getReference(), app.getStatus(), app.getFullName(), app.getEmail(), app.getPhone(),
                  app.getPhotoUrl(), app.getSubmittedAt(), app.getDecidedAt(), checks.score(), checks.level(), checks.levelLabel(),
                  (int) asked.stream().filter(g -> "Confirmed".equals(g.getStatus())).count(), asked.size(),
                  app.getAmountPaid(), app.getMemberId());
            })
            .toList();
    return ResponseEntity.ok(items);
  }

  @GetMapping("/api/v1/cooperatives/{id}/membership-applications/{applicationId}")
  public ResponseEntity<?> detail(Authentication authentication, @PathVariable String id, @PathVariable String applicationId) {
    requireAccess(authentication, id, "Members Directory", false);
    Cooperative coop = findCoop(id);
    MembershipApplication app = findApplication(id, applicationId);
    service.lookUpBankName(app);
    return ResponseEntity.ok(detailOf(app, coop));
  }

  @PostMapping("/api/v1/cooperatives/{id}/membership-applications/{applicationId}/approve")
  public ResponseEntity<?> approve(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String applicationId,
      @Valid @RequestBody ApproveRequest request,
      HttpServletRequest httpRequest) {
    Member caller = requireAccess(authentication, id, "Members Directory", true);
    Cooperative coop = findCoop(id);
    MembershipApplication app = findApplication(id, applicationId);
    Member member = service.approve(app, coop, caller.getId(), request.membershipId(), request.note());
    auditLogService.log(caller.getId(), caller.getRole(), "Members", "Approve", member.getFullName() + " (" + app.getReference() + ")", "Success", httpRequest);
    return ResponseEntity.ok(detailOf(app, coop));
  }

  @PostMapping("/api/v1/cooperatives/{id}/membership-applications/{applicationId}/reject")
  public ResponseEntity<?> reject(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String applicationId,
      @Valid @RequestBody RejectRequest request,
      HttpServletRequest httpRequest) {
    Member caller = requireAccess(authentication, id, "Members Directory", true);
    Cooperative coop = findCoop(id);
    MembershipApplication app = findApplication(id, applicationId);
    service.reject(app, coop, caller.getId(), request.reason().trim());
    auditLogService.log(caller.getId(), caller.getRole(), "Members", "Reject", app.getFullName() + " (" + app.getReference() + ")", "Warning", httpRequest);
    return ResponseEntity.ok(detailOf(app, coop));
  }

  private ApplicationViews.AdminDetail detailOf(MembershipApplication app, Cooperative coop) {
    RegistrationSettings settings = service.settingsFor(coop.getId());
    List<ApplicationGuarantor> guarantors = service.guarantorsOf(app);
    String decidedBy =
        app.getDecidedBy() == null ? null : memberRepository.findById(app.getDecidedBy()).map(Member::getFullName).orElse(app.getDecidedBy());
    return new ApplicationViews.AdminDetail(
        ApplicationViews.ApplicantView.of(app, coop, settings, guarantors),
        app.getCreatedAt(),
        app.getEmailVerifiedAt(),
        app.getBankAccountName(),
        app.getPaymentReference(),
        app.getPaymentGateway(),
        app.getAmountPaid(),
        app.getPaidAt(),
        app.getDecidedAt(),
        decidedBy,
        app.getDecisionNote(),
        app.getMemberId(),
        applicationChecks.run(app, coop, settings, guarantors),
        service.savingsTypeName(settings),
        MembershipApplication.SUBMITTED.equals(app.getStatus()) ? memberIdGenerator.nextFor(coop) : null);
  }

  private SettingsDto toDto(RegistrationSettings settings) {
    String coopId = settings.getCooperativeId();
    return new SettingsDto(
        settings.isOpen(),
        settings.getFormFee(),
        settings.getFirstSavings(),
        settings.totalFee(),
        settings.getSavingsTypeId() == null ? null : settings.getSavingsTypeId().toString(),
        settings.getRules(),
        settings.getRequirements(),
        RegistrationSettings.DEFAULT_REQUIREMENTS,
        settings.isRequireIdDocument(),
        settings.getMinAge(),
        service.canPayOnline(coopId),
        applicationRepository.countByCooperativeIdAndStatus(coopId, MembershipApplication.SUBMITTED));
  }

  /** Super admin, or this co-op's admin/staff with the given access. */
  private Member requireAccess(Authentication authentication, String cooperativeId, String module, boolean write) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) throw new MembershipApplicationService.ApplicationProblem(401, "Member no longer exists");
    if ("super_admin".equals(caller.getRole())) return caller;
    // Joining settings live under Settings → Co-operative Settings.
    String tab = "Settings".equals(module) ? "Co-operative Settings" : null;
    if (coopPermissions.allows(caller, cooperativeId, module, tab, write) && coopPermissions.isStaff(caller, cooperativeId)) {
      return caller;
    }
    throw new MembershipApplicationService.ApplicationProblem(403, "Your role doesn't allow this.");
  }

  private Cooperative findCoop(String id) {
    return cooperativeRepository.findById(id)
        .orElseThrow(() -> new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that co-operative"));
  }

  private MembershipApplication findApplication(String cooperativeId, String applicationId) {
    try {
      return applicationRepository.findById(UUID.fromString(applicationId))
          .filter(app -> cooperativeId.equals(app.getCooperativeId()) && !app.isDraft())
          .orElseThrow(() -> new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that application."));
    } catch (IllegalArgumentException e) {
      throw new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that application.");
    }
  }
}
