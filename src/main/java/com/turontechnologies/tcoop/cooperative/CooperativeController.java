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
import java.security.SecureRandom;
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
 * the co-op's first admin account and emails them their login credentials), read, update, and
 * activate/disable. See documentation/flows.md for the full onboarding sequence.
 */
@RestController
public class CooperativeController {

  private static final Logger log = LoggerFactory.getLogger(CooperativeController.class);
  private static final List<String> MEMBER_ROLES = List.of("admin", "member");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String TEMP_PASSWORD_CHARS =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

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

    String adminId = generateAdminId();
    String temporaryPassword = generateTemporaryPassword();
    Member admin =
        new Member(
            adminId,
            coop.getId(),
            "admin",
            passwordEncoder.encode(temporaryPassword),
            request.adminFirstName(),
            request.adminLastName(),
            request.contactEmail());
    memberRepository.save(admin);

    // The co-op and admin account both already exist at this point regardless of whether the
    // email below succeeds — onboarding isn't rolled back on a delivery failure, since a
    // half-created co-op with no way to retry (coopId now taken) would be worse than a fully
    // created one whose admin just needs to use "Forgot password" to get in. Delivery failure
    // is logged, not surfaced as a request failure.
    try {
      emailService.sendAdminWelcomeEmail(
          request.contactEmail(), admin.getFullName(), coop.getName(), adminId, temporaryPassword);
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
        request.contactEmail(),
        request.contactPhone(),
        request.address(),
        request.country(),
        request.state(),
        request.city());
    cooperativeRepository.save(coop);

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

  private String generateAdminId() {
    long n = memberRepository.countByRoleIn(List.of("admin")) + 1;
    String candidate;
    do {
      candidate = String.format("AD-%04d", n++);
    } while (memberRepository.existsById(candidate));
    return candidate;
  }

  private String generateTemporaryPassword() {
    StringBuilder password = new StringBuilder(12);
    for (int i = 0; i < 12; i++) {
      password.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
    }
    return password.toString();
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
