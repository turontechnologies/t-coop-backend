package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin-only co-operative onboarding and management — list, create (which also provisions
 * the co-op's admin account — the co-op logs in as itself, using its own co-op ID and the
 * platform default password — and emails the admin their login details), read, update, and
 * activate/disable. See documentation/flows.md for the full onboarding sequence.
 */
@RestController
public class CooperativeController {

  private static final Logger log = LoggerFactory.getLogger(CooperativeController.class);
  private static final List<String> MEMBER_ROLES = List.of("admin", "member");

  /** Every co-op's admin account starts with this password; they're expected to change it. */
  private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final AuditLogService auditLogService;

  public CooperativeController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsRecordRepository savingsRecordRepository,
      LoanRecordRepository loanRecordRepository,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<CooperativeSummaryDto> dtos =
        cooperativeRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/api/v1/cooperatives/{id}")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    return ResponseEntity.ok(toDto(coop));
  }

  @PostMapping("/api/v1/cooperatives")
  public ResponseEntity<?> create(
      Authentication authentication,
      @Valid @RequestBody CooperativeCreateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    if (cooperativeRepository.existsById(request.coopId())) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That co-op ID is already in use. Please choose another."));
    }
    if (memberRepository.findByEmail(request.contactEmail()).isPresent()) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That email address is already in use by another account"));
    }
    if (memberRepository.existsById(request.coopId())) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That co-op ID is already in use. Please choose another."));
    }

    Cooperative coop =
        new Cooperative(
            request.coopId(),
            request.coopName(),
            request.adminFirstName() + " " + request.adminLastName(),
            request.contactEmail(),
            request.contactPhone(),
            request.address(),
            request.country(),
            request.state(),
            request.city());
    cooperativeRepository.save(coop);

    // The co-op IS the admin account — it logs in with its own co-op ID, not a separately
    // generated one, so that "how many co-ops has super admin onboarded" and "how many admins
    // exist" are always the same number by construction.
    Member admin =
        new Member(
            coop.getId(),
            coop.getId(),
            "admin",
            passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD),
            request.adminFirstName(),
            request.adminLastName(),
            request.contactEmail());
    // The create constructor only sets id/role/passwordHash/firstName/lastName/email — the
    // rest of the admin's own profile (address, phone, country/state/city) starts out matching
    // the co-op's own details rather than sitting blank until the admin fills it in themselves.
    admin.updateProfile(
        request.adminFirstName(),
        request.adminLastName(),
        null,
        null,
        request.contactPhone(),
        request.contactEmail(),
        null,
        request.address(),
        request.country(),
        request.state(),
        request.city(),
        null,
        null,
        null,
        null,
        null,
        null);
    memberRepository.save(admin);

    // The co-op and admin account both already exist at this point regardless of whether the
    // email below succeeds — onboarding isn't rolled back on a delivery failure, since a
    // half-created co-op with no way to retry (coopId now taken) would be worse than a fully
    // created one whose admin just needs to use "Forgot password" to get in. Delivery failure
    // is logged, not surfaced as a request failure.
    try {
      emailService.sendAdminWelcomeEmail(
          request.contactEmail(),
          admin.getFullName(),
          coop.getName(),
          coop.getId(),
          DEFAULT_ADMIN_PASSWORD);
    } catch (EmailDeliveryException e) {
      log.warn(
          "Co-op {} created but welcome email to {} failed: {}",
          coop.getId(),
          request.contactEmail(),
          e.getMessage());
    }

    auditLogService.log(
        adminIdOf(authentication),
        "super_admin",
        "Co-operatives",
        "Create",
        coop.getName(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(toDto(coop));
  }

  @PatchMapping("/api/v1/cooperatives/{id}")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CooperativeUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    coop.updateDetails(
        request.name(),
        request.adminFirstName() + " " + request.adminLastName(),
        request.contactEmail(),
        request.contactPhone(),
        request.address(),
        request.country(),
        request.state(),
        request.city());
    cooperativeRepository.save(coop);

    // The co-op's admin Member row (id == coop id) is what the admin actually logs in and edits
    // their own profile as, so a super-admin edit here has to reach it too — otherwise the admin
    // portal would keep showing the old name/email/phone after this save. Every other profile
    // field (NIN, bank details, etc.) is preserved as-is since this form doesn't touch them.
    Member admin = memberRepository.findById(id).orElse(null);
    if (admin != null) {
      admin.updateProfile(
          request.adminFirstName(),
          request.adminLastName(),
          admin.getOtherName(),
          admin.getGender(),
          request.contactPhone(),
          request.contactEmail(),
          admin.getNin(),
          request.address(),
          request.country(),
          request.state(),
          request.city(),
          admin.getFacebook(),
          admin.getTwitter(),
          admin.getGuarantor(),
          admin.getBankCode(),
          admin.getAccountNumber(),
          admin.getAccountName());
      memberRepository.save(admin);
    }

    auditLogService.log(
        adminIdOf(authentication),
        "super_admin",
        "Co-operatives",
        "Update",
        coop.getName(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(toDto(coop));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/status")
  public ResponseEntity<?> updateStatus(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CooperativeStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    coop.setStatus(request.status());
    cooperativeRepository.save(coop);

    // The admin logs in as the co-op, so disabling the co-op has to lock that login out too.
    // Member.status only allows Active/Inactive (Cooperative uses Active/Disabled), so map
    // Disabled -> Inactive here.
    Member admin = memberRepository.findById(id).orElse(null);
    if (admin != null) {
      admin.setStatus("Disabled".equals(request.status()) ? "Inactive" : "Active");
      memberRepository.save(admin);
    }

    auditLogService.log(
        adminIdOf(authentication),
        "super_admin",
        "Co-operatives",
        "Update",
        coop.getName(),
        "Disabled".equals(request.status()) ? "Warning" : "Success",
        httpRequest);

    return ResponseEntity.ok(toDto(coop));
  }

  private CooperativeSummaryDto toDto(Cooperative coop) {
    long memberCount = memberRepository.countByCooperativeIdAndRoleIn(coop.getId(), MEMBER_ROLES);
    var totalSavings = savingsRecordRepository.sumByCooperative(coop.getId());
    var totalLoans = loanRecordRepository.sumByCooperative(coop.getId());
    return new CooperativeSummaryDto(
        coop.getId(),
        coop.getName(),
        coop.getAdminName(),
        coop.getContactEmail(),
        coop.getContactPhone(),
        coop.getAddress(),
        coop.getCountry(),
        coop.getState(),
        coop.getCity(),
        coop.getStatus(),
        coop.getCurrency(),
        memberCount,
        totalSavings,
        totalLoans);
  }

  private String adminIdOf(Authentication authentication) {
    return (String) authentication.getPrincipal();
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage co-operatives"));
    }
    return null;
  }
}
