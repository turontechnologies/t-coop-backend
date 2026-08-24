package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.notification.NotificationService;
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
  private final SavingsTypeRepository savingsTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;

  public CooperativeController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsTypeRepository savingsTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanTypeRepository loanTypeRepository,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      AuditLogService auditLogService,
      NotificationService notificationService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
  }

  @GetMapping("/api/v1/cooperatives")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<CooperativeSummaryDto> dtos =
        cooperativeRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  /** Super admin can view any co-op; an admin can also view their own now — needed for their
   * Settings -> Co-operative/Savings/Loans tabs, which all read this before editing. */
  @GetMapping("/api/v1/cooperatives/{id}")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    return ResponseEntity.ok(toDto(coop));
  }

  /** Listing is read-only for super admin's co-op oversight; an admin can also list (and add
   * to, below) their own co-op's roster — this is the real backend behind Members Directory. */
  @GetMapping("/api/v1/cooperatives/{id}/members")
  public ResponseEntity<?> members(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<CoopMemberDto> dtos =
        memberRepository.findAllByCooperativeId(id).stream()
            .filter(member -> MEMBER_ROLES.contains(member.getRole()))
            .map(CoopMemberDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** Adds a real member with a real login — same convention as co-op admin onboarding: the
   * caller picks the membership ID, the account starts with the platform default password,
   * a welcome email goes out. An admin can only add to their own co-op; super admin, any co-op. */
  @PostMapping("/api/v1/cooperatives/{id}/members")
  public ResponseEntity<?> addMember(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody MemberCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    if (memberRepository.existsById(request.membershipId())) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That membership ID is already in use. Please choose another."));
    }
    if (memberRepository.findByEmail(request.email()).isPresent()) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That email address is already in use by another account"));
    }

    String role = "Admin".equals(request.role()) ? "admin" : "member";
    Member member =
        new Member(
            request.membershipId(),
            id,
            role,
            passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD),
            request.firstName(),
            request.lastName(),
            request.email());
    member.updateProfile(
        request.firstName(),
        request.lastName(),
        request.otherName(),
        request.gender(),
        request.phone(),
        request.email(),
        null,
        request.homeAddress(),
        request.country(),
        request.state(),
        request.city(),
        request.facebook(),
        request.twitter(),
        request.guarantor(),
        request.bankCode(),
        request.accountNumber(),
        request.accountName());
    memberRepository.save(member);

    try {
      emailService.sendMemberWelcomeEmail(
          request.email(),
          member.getFullName(),
          cooperativeRepository.findById(id).map(Cooperative::getName).orElse(id),
          member.getId(),
          DEFAULT_ADMIN_PASSWORD);
    } catch (EmailDeliveryException e) {
      log.warn(
          "Member {} added to {} but welcome email to {} failed: {}",
          member.getId(),
          id,
          request.email(),
          e.getMessage());
    }

    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Members",
        "Create",
        member.getFullName(),
        "Success",
        httpRequest);

    notificationService.notify(
        member.getId(),
        "MEMBER_ADDED",
        "Welcome to " + cooperativeRepository.findById(id).map(Cooperative::getName).orElse(id),
        "Your account is ready. Log in with membership ID " + member.getId() + " to get started.",
        "/profile");

    return ResponseEntity.ok(CoopMemberDto.from(member));
  }

  /** Edits a member's profile — same access rule as addMember: admin only their own co-op,
   * super admin any. Fields the frontend's edit form doesn't show (otherName, gender, phone,
   * nin, facebook, twitter) are preserved as-is, never blanked out. */
  @PatchMapping("/api/v1/cooperatives/{id}/members/{memberId}")
  public ResponseEntity<?> updateMember(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String memberId,
      @Valid @RequestBody MemberUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }

    member.updateProfile(
        request.firstName(),
        request.lastName(),
        member.getOtherName(),
        member.getGender(),
        member.getPhone(),
        request.email(),
        member.getNin(),
        member.getHomeAddress(),
        request.country(),
        request.state(),
        request.city(),
        member.getFacebook(),
        member.getTwitter(),
        request.guarantor(),
        request.bankCode(),
        request.accountNumber(),
        request.accountName());
    member.setRole("Admin".equals(request.role()) ? "admin" : "member");
    memberRepository.save(member);

    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Members",
        "Update",
        member.getFullName(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(CoopMemberDto.from(member));
  }

  /** Activates/disables a member's login — same access rule as addMember/updateMember. */
  @PatchMapping("/api/v1/cooperatives/{id}/members/{memberId}/status")
  public ResponseEntity<?> updateMemberStatus(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String memberId,
      @Valid @RequestBody MemberStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }

    member.setStatus(request.status());
    memberRepository.save(member);

    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Members",
        "Update",
        member.getFullName(),
        "Inactive".equals(request.status()) ? "Warning" : "Success",
        httpRequest);

    notificationService.notify(
        member.getId(),
        "MEMBER_STATUS",
        "Active".equals(request.status()) ? "Your account was reactivated" : "Your account was disabled",
        "Active".equals(request.status())
            ? "Your account is active again — you can log in as usual."
            : "Your account has been disabled. Contact your co-operative's admin if this is unexpected.",
        "/profile");

    return ResponseEntity.ok(CoopMemberDto.from(member));
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

    notificationService.notify(
        admin.getId(),
        "COOPERATIVE_WELCOME",
        "Welcome to T-Coop",
        coop.getName() + " is set up and ready to go. Log in with membership ID " + admin.getId()
            + " to get started.",
        "/profile");

    return ResponseEntity.ok(toDto(coop));
  }

  /** A super admin can edit any co-op; an admin can only edit their own (matching the same
   * access rule as Members management) — this used to be super-admin-only, opened up so an
   * admin can maintain their own co-op's details, currency, and withdrawal fee. */
  @PatchMapping("/api/v1/cooperatives/{id}")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CooperativeUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

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
    if (request.currency() != null && !request.currency().isBlank()) {
      coop.setCurrency(request.currency());
    }
    if (request.withdrawalFeePercent() != null) {
      coop.setWithdrawalFeePercent(request.withdrawalFeePercent());
    }
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
        access.caller().getRole(),
        "Co-operatives",
        "Update",
        coop.getName(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(toDto(coop));
  }

  /** The co-op's own receiving account — a super admin can view/edit any, an admin only their
   * own, same {@link #requireCoopAccess} rule as everything else scoped to one co-op. */
  @PatchMapping("/api/v1/cooperatives/{id}/bank-account")
  public ResponseEntity<?> updateBankAccount(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CoopBankAccountUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    coop.updateBankAccount(request.bankCode(), request.accountNumber(), request.accountName());
    cooperativeRepository.save(coop);

    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Co-operatives",
        "Update",
        coop.getName() + " (bank account)",
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

    notificationService.notifyCoopAdmin(
        coop.getId(),
        "COOPERATIVE_STATUS",
        "Disabled".equals(request.status()) ? "Your co-operative was disabled" : "Your co-operative was re-enabled",
        "Disabled".equals(request.status())
            ? coop.getName() + " has been disabled by the platform. Contact support for details."
            : coop.getName() + " is active again — you and your members can log in as usual.",
        "/profile");

    return ResponseEntity.ok(toDto(coop));
  }

  private CooperativeSummaryDto toDto(Cooperative coop) {
    long memberCount = memberRepository.countByCooperativeIdAndRoleIn(coop.getId(), MEMBER_ROLES);
    long savingsTypeCount = savingsTypeRepository.countByCooperativeId(coop.getId());
    long loanTypeCount = loanTypeRepository.countByCooperativeId(coop.getId());
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
        coop.getWithdrawalFeePercent(),
        coop.getBankCode(),
        coop.getAccountNumber(),
        coop.getAccountName(),
        memberCount,
        savingsTypeCount,
        loanTypeCount,
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

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  /** Super admin can access any co-op's members; an admin only their own — never trusts the
   * path's {id} for an admin caller, always checks it against their own cooperativeId. */
  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    if ("admin".equals(caller.getRole()) && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You can only manage your own co-operative's members")));
  }
}
