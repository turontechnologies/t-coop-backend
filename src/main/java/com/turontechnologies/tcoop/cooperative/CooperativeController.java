package com.turontechnologies.tcoop.cooperative;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberGuarantor;
import com.turontechnologies.tcoop.member.MemberGuarantorDto;
import com.turontechnologies.tcoop.member.MemberGuarantorRepository;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
  private static final int GUARANTOR_INVITE_VALID_DAYS = 14;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Every co-op's admin account starts with this password; they're expected to change it. */
  private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final MemberGuarantorRepository memberGuarantorRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final Cloudinary cloudinary;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  public CooperativeController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      MemberGuarantorRepository memberGuarantorRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsTypeRepository savingsTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanTypeRepository loanTypeRepository,
      PlatformSettingsRepository platformSettingsRepository,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      AuditLogService auditLogService,
      NotificationService notificationService,
      Cloudinary cloudinary) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.memberGuarantorRepository = memberGuarantorRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
    this.cloudinary = cloudinary;
  }

  @GetMapping("/api/v1/cooperatives")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<CooperativeSummaryDto> dtos =
        cooperativeRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  /** Just the co-op's name and logo — accessible to ANY of its own members (not only admin/coop
   * staff, unlike every other endpoint here), so even a plain member's dashboard can show which
   * co-operative they belong to without exposing bank details or subscription info. */
  @GetMapping("/api/v1/cooperatives/{id}/branding")
  public ResponseEntity<?> branding(Authentication authentication, @PathVariable String id) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    boolean allowed = "super_admin".equals(caller.getRole()) || id.equals(caller.getCooperativeId());
    if (!allowed) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "You can only view your own co-operative's branding"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    return ResponseEntity.ok(CooperativeBrandingDto.from(coop));
  }

  /** Uploads the co-op's own logo — same access rule as addMember/update: the admin can only set
   * their own co-op's, super admin any (typically right after onboarding a new co-op). Mirrors
   * UploadController's avatar upload (same Cloudinary config, same response shape). */
  @PostMapping(value = "/api/v1/cooperatives/{id}/logo", consumes = "multipart/form-data")
  public ResponseEntity<?> uploadLogo(
      Authentication authentication, @PathVariable String id, @RequestParam("file") MultipartFile file) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
    }
    if (!Set.of("image/png", "image/jpeg", "image/webp").contains(file.getContentType())) {
      return ResponseEntity.badRequest().body(Map.of("error", "Only PNG, JPEG, or WEBP images are allowed"));
    }
    if (file.getSize() > 5L * 1024 * 1024) {
      return ResponseEntity.badRequest().body(Map.of("error", "Image must be 5MB or smaller"));
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result =
          cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("folder", "t-coop/logos"));
      String logoUrl = (String) result.get("secure_url");
      coop.setLogoUrl(logoUrl);
      cooperativeRepository.save(coop);
      return ResponseEntity.ok(Map.of("url", logoUrl));
    } catch (Exception exception) {
      return ResponseEntity.status(502).body(Map.of("error", "Upload to Cloudinary failed"));
    }
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

  /** Status of a member's named guarantors — Pending until each one clicks through their own
   * email invite (see GuarantorInviteController for the public accept/decline side). */
  @GetMapping("/api/v1/cooperatives/{id}/members/{memberId}/guarantors")
  public ResponseEntity<?> memberGuarantors(
      Authentication authentication, @PathVariable String id, @PathVariable String memberId) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }

    List<MemberGuarantorDto> dtos =
        memberGuarantorRepository.findAllByMemberId(memberId).stream().map(MemberGuarantorDto::from).toList();
    return ResponseEntity.ok(dtos);
  }

  /** Preview of the next auto-generated member id, per this co-op's own configured format
   * (Settings -> Co-operative -> Member ID Format) — see {@link #nextGeneratedId} for how it's
   * computed. Scoped to this one co-op's own members only, never another co-op's. */
  @GetMapping("/api/v1/cooperatives/{id}/members/next-id")
  public ResponseEntity<?> nextMemberId(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<String> existingIds = memberRepository.findAllByCooperativeId(id).stream().map(Member::getId).toList();
    String nextId =
        nextGeneratedId(coop.getMemberIdPrefix(), coop.getMemberIdType(), coop.getMemberIdPadding(), existingIds);
    return ResponseEntity.ok(Map.of("nextId", nextId));
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
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
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
    var guarantorError = validateGuarantors(request.guarantors(), coop, id);
    if (guarantorError != null) return guarantorError;

    String role = "Admin".equals(request.role()) ? "admin" : "member";
    String guarantorNames =
        request.guarantors().stream().map(GuarantorInput::name).collect(Collectors.joining(", "));
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
        request.nin(),
        request.homeAddress(),
        request.country(),
        request.state(),
        request.city(),
        request.facebook(),
        request.twitter(),
        guarantorNames,
        request.nextOfKinName(),
        request.nextOfKinPhone(),
        request.nextOfKinEmail(),
        request.nextOfKinRelationship(),
        request.nextOfKinAuthorityLevel(),
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

    sendGuarantorInvites(request.guarantors(), member, coop);

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
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
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
        member.getNextOfKinName(),
        member.getNextOfKinPhone(),
        member.getNextOfKinEmail(),
        member.getNextOfKinRelationship(),
        member.getNextOfKinAuthorityLevel(),
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

  /** Preview of the next auto-generated co-op id, per the super admin's own configured format
   * (Settings -> Payment Settings -> Fees & Charges -> Co-op ID Format). Computed from the
   * highest existing suffix matching that prefix, not a count — so it never collides with a gap
   * left by a manually-typed id from before this feature existed (e.g. the real "COOP-0001" /
   * "coop-0002" mismatch this feature exists to clean up going forward). */
  @GetMapping("/api/v1/cooperatives/next-id")
  public ResponseEntity<?> nextId(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformSettings settings = platformSettingsRepository.findById(1).orElse(null);
    String prefix = settings != null && settings.getCoopIdPrefix() != null ? settings.getCoopIdPrefix() : "COOP";
    int padding = settings != null && settings.getCoopIdPadding() > 0 ? settings.getCoopIdPadding() : 4;
    String type = settings != null && settings.getCoopIdType() != null ? settings.getCoopIdType() : "NUMERIC";

    List<String> existingIds = cooperativeRepository.findAll().stream().map(Cooperative::getId).toList();
    String nextId = nextGeneratedId(prefix, type, padding, existingIds);
    return ResponseEntity.ok(Map.of("nextId", nextId));
  }

  private static final String NUMERIC_DIGITS = "0123456789";
  private static final String ALPHA_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String ALPHANUMERIC_DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private String digitAlphabetFor(String idType) {
    return switch (idType == null ? "NUMERIC" : idType) {
      case "ALPHA" -> ALPHA_DIGITS;
      case "ALPHANUMERIC" -> ALPHANUMERIC_DIGITS;
      default -> NUMERIC_DIGITS;
    };
  }

  /** Shared by both the co-op-id and member-id generators — parses every existing id matching
   * {@code PREFIX-<suffix>} (case-insensitive) where {@code <suffix>} is made of characters from
   * the chosen type's digit alphabet (NUMERIC = 0-9, ALPHA = A-Z, ALPHANUMERIC = 0-9 then A-Z),
   * decodes each as a base-N number, takes the highest value found, and re-encodes it + 1 back
   * into that same alphabet, left-padded to {@code padding} characters wide. Switching a co-op's
   * (or the platform's) id type after ids already exist in a different alphabet just means none
   * of those old ids match the new pattern — generation starts fresh from 1 in the new scheme,
   * which is the only sane behavior for a deliberate format change. */
  private String nextGeneratedId(String prefix, String idType, int padding, List<String> existingIds) {
    String digits = digitAlphabetFor(idType);
    int base = digits.length();
    Pattern pattern =
        Pattern.compile("^" + Pattern.quote(prefix) + "-?([" + digits + "]+)$", Pattern.CASE_INSENSITIVE);
    long max =
        existingIds.stream()
            .map(pattern::matcher)
            .filter(Matcher::matches)
            .map(matcher -> decodeBaseN(matcher.group(1).toUpperCase(), digits, base))
            .max(Comparator.naturalOrder())
            .orElse(0L);
    return prefix + "-" + encodeBaseN(max + 1, digits, base, padding);
  }

  private long decodeBaseN(String value, String digits, int base) {
    long result = 0;
    for (char c : value.toCharArray()) {
      result = result * base + digits.indexOf(c);
    }
    return result;
  }

  private String encodeBaseN(long value, String digits, int base, int padding) {
    StringBuilder encoded = new StringBuilder();
    long remaining = value;
    do {
      encoded.append(digits.charAt((int) (remaining % base)));
      remaining /= base;
    } while (remaining > 0);
    while (encoded.length() < padding) {
      encoded.append(digits.charAt(0));
    }
    return encoded.reverse().toString();
  }

  /** Enforces the co-op's own configured guarantor rule: at least {@code minGuarantors}
   * guarantors, and at least one of them has to be an existing member of this co-op (matched by
   * email, more reliable than name) — the rest can be anyone; every guarantor still has to
   * accept their own email invite regardless (see {@link #sendGuarantorInvites}). Returns null
   * when the rule is satisfied, or the 400 response to send back otherwise. */
  private ResponseEntity<?> validateGuarantors(
      List<GuarantorInput> guarantors, Cooperative coop, String cooperativeId) {
    if (guarantors.size() < coop.getMinGuarantors()) {
      return ResponseEntity.status(400)
          .body(Map.of("error", "This co-operative requires at least " + coop.getMinGuarantors() + " guarantors."));
    }
    Set<String> existingMemberEmails =
        memberRepository.findAllByCooperativeId(cooperativeId).stream()
            .map(existing -> existing.getEmail().trim().toLowerCase())
            .collect(Collectors.toSet());
    boolean hasExistingMemberGuarantor =
        guarantors.stream().anyMatch(g -> existingMemberEmails.contains(g.email().trim().toLowerCase()));
    if (!hasExistingMemberGuarantor) {
      return ResponseEntity.status(400)
          .body(
              Map.of(
                  "error",
                  "At least one guarantor must be an existing member of this co-operative (matched by email)."));
    }
    return null;
  }

  /** Creates a Pending MemberGuarantor row per guarantor and emails each an accept/decline link —
   * a delivery failure is logged, not fatal, same discipline as the member welcome email above. */
  private void sendGuarantorInvites(List<GuarantorInput> guarantors, Member member, Cooperative coop) {
    for (GuarantorInput guarantorInput : guarantors) {
      MemberGuarantor guarantor =
          new MemberGuarantor(
              member.getId(), coop.getId(), guarantorInput.name(), guarantorInput.email(), guarantorInput.phone());
      String token = generateGuarantorToken();
      guarantor.setAcceptToken(token, LocalDateTime.now(ZoneOffset.UTC).plusDays(GUARANTOR_INVITE_VALID_DAYS));
      memberGuarantorRepository.save(guarantor);

      try {
        emailService.sendGuarantorRequestEmail(
            guarantorInput.email(),
            guarantorInput.name(),
            member.getFullName(),
            coop.getName(),
            frontendUrl + "/guarantor-invite/" + token);
      } catch (EmailDeliveryException e) {
        log.warn(
            "Guarantor invite for member {} to {} failed: {}",
            member.getId(),
            guarantorInput.email(),
            e.getMessage());
      }
    }
  }

  private String generateGuarantorToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
            request.city(),
            request.currency());
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
        request.adminNin(),
        request.address(),
        request.country(),
        request.state(),
        request.city(),
        null,
        null,
        null,
        null,
        null,
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
    if (request.withdrawalFeeAmount() != null && request.withdrawalFeeType() != null) {
      coop.setWithdrawalFee(request.withdrawalFeeAmount(), request.withdrawalFeeType());
    }
    if (request.memberIdPrefix() != null && request.memberIdPadding() != null) {
      coop.updateMemberIdFormat(
          request.memberIdPrefix().toUpperCase(),
          request.memberIdPadding(),
          request.memberIdType() != null ? request.memberIdType() : coop.getMemberIdType());
    }
    if (request.minGuarantors() != null) {
      coop.setMinGuarantors(request.minGuarantors());
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
          request.adminNin() != null && !request.adminNin().isBlank() ? request.adminNin() : admin.getNin(),
          request.address(),
          request.country(),
          request.state(),
          request.city(),
          admin.getFacebook(),
          admin.getTwitter(),
          admin.getGuarantor(),
          admin.getNextOfKinName(),
          admin.getNextOfKinPhone(),
          admin.getNextOfKinEmail(),
          admin.getNextOfKinRelationship(),
          admin.getNextOfKinAuthorityLevel(),
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

  /** Hands the co-op's admin identity (the Member row whose id equals the co-op's own id) to a
   * new person. The outgoing admin doesn't lose access — they become a regular member under a
   * freshly generated membership id (per this co-op's own member-id format), keeping every
   * profile field they had (including their savings/loan history — see {@link #reassignRecords}),
   * just with role/password reset the same way any new member starts. If the incoming admin's
   * email already belongs to an existing member of this same co-op, that member is promoted in
   * place instead of being blocked as a conflict: their profile and their own savings/loan
   * history move onto the admin identity, and their old membership row is retired (kept, not
   * deleted, so notifications/audit log/notices already pointing at it stay valid — see
   * {@link #retireMember}). Otherwise the incoming admin's row gets fresh contact details, a
   * reset default password, and every other personal field cleared since they're a genuinely
   * new person. Same {@link #requireCoopAccess} rule as everything else scoped to one co-op, so
   * either a super admin or the outgoing admin themselves can do this handover. Wrapped in one
   * transaction — a failure partway through (an unmapped notification type once did exactly
   * this) must not leave the admin identity half-swapped. */
  @PostMapping("/api/v1/cooperatives/{id}/transfer-admin")
  @Transactional
  public ResponseEntity<?> transferAdmin(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody TransferAdminRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    Member outgoingAdmin = memberRepository.findById(id).orElse(null);
    if (outgoingAdmin == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find this co-operative's admin"));
    }

    Member promotedMember =
        memberRepository
            .findByEmail(request.newEmail())
            .filter(existing -> !existing.getId().equals(id))
            .filter(existing -> id.equals(existing.getCooperativeId()))
            .orElse(null);
    if (promotedMember == null) {
      boolean emailTakenByAnotherMember =
          memberRepository.findByEmail(request.newEmail()).filter(existing -> !existing.getId().equals(id)).isPresent();
      if (emailTakenByAnotherMember) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "That email address is already in use by another account"));
      }
    }

    // members.email carries a UNIQUE constraint — the outgoing admin's OLD email has to be freed
    // up (by moving the admin row to the NEW email first, flushed immediately) before the new
    // member row can be inserted holding that same OLD email, or the INSERT collides with the
    // admin row that — at that instant — still holds it. Captured up front since updateProfile
    // below overwrites outgoingAdmin's own fields in place.
    String oldFirstName = outgoingAdmin.getFirstName();
    String oldLastName = outgoingAdmin.getLastName();
    String oldOtherName = outgoingAdmin.getOtherName();
    String oldGender = outgoingAdmin.getGender();
    String oldPhone = outgoingAdmin.getPhone();
    String oldEmail = outgoingAdmin.getEmail();
    String oldNin = outgoingAdmin.getNin();
    String oldHomeAddress = outgoingAdmin.getHomeAddress();
    String oldCountry = outgoingAdmin.getCountry();
    String oldState = outgoingAdmin.getState();
    String oldCity = outgoingAdmin.getCity();
    String oldFacebook = outgoingAdmin.getFacebook();
    String oldTwitter = outgoingAdmin.getTwitter();
    String oldGuarantor = outgoingAdmin.getGuarantor();
    String oldNextOfKinName = outgoingAdmin.getNextOfKinName();
    String oldNextOfKinPhone = outgoingAdmin.getNextOfKinPhone();
    String oldNextOfKinEmail = outgoingAdmin.getNextOfKinEmail();
    String oldNextOfKinRelationship = outgoingAdmin.getNextOfKinRelationship();
    String oldNextOfKinAuthorityLevel = outgoingAdmin.getNextOfKinAuthorityLevel();
    String oldBankCode = outgoingAdmin.getBankCode();
    String oldAccountNumber = outgoingAdmin.getAccountNumber();
    String oldAccountName = outgoingAdmin.getAccountName();
    String oldPasswordHash = outgoingAdmin.getPasswordHash();

    // Promoting an existing member: their own profile carries over onto the admin identity (not
    // just the bare name/phone/nin the request form collects), and their old row — which is about
    // to hand its email to the admin row — has to be retired (email changed, status Inactive)
    // and flushed first, or the swap below collides with it the same way the outgoing admin's own
    // old email would.
    String promotedMemberOldId = promotedMember == null ? null : promotedMember.getId();
    if (promotedMember != null) {
      retireMember(promotedMember);
      memberRepository.saveAndFlush(promotedMember);
    }

    outgoingAdmin.updateProfile(
        request.newFirstName(),
        request.newLastName(),
        promotedMember == null ? null : promotedMember.getOtherName(),
        promotedMember == null ? null : promotedMember.getGender(),
        request.newPhone(),
        request.newEmail(),
        request.newNin(),
        promotedMember == null ? null : promotedMember.getHomeAddress(),
        coop.getCountry(),
        coop.getState(),
        coop.getCity(),
        promotedMember == null ? null : promotedMember.getFacebook(),
        promotedMember == null ? null : promotedMember.getTwitter(),
        promotedMember == null ? null : promotedMember.getGuarantor(),
        promotedMember == null ? null : promotedMember.getNextOfKinName(),
        promotedMember == null ? null : promotedMember.getNextOfKinPhone(),
        promotedMember == null ? null : promotedMember.getNextOfKinEmail(),
        promotedMember == null ? null : promotedMember.getNextOfKinRelationship(),
        promotedMember == null ? null : promotedMember.getNextOfKinAuthorityLevel(),
        promotedMember == null ? null : promotedMember.getBankCode(),
        promotedMember == null ? null : promotedMember.getAccountNumber(),
        promotedMember == null ? null : promotedMember.getAccountName());
    outgoingAdmin.changePassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
    // Flushed immediately (not just save()'d) so this UPDATE — freeing up oldEmail — actually
    // reaches the database before the INSERT below tries to reuse it. See the comment above.
    memberRepository.saveAndFlush(outgoingAdmin);

    List<String> existingIds = memberRepository.findAllByCooperativeId(id).stream().map(Member::getId).toList();
    String newMemberId =
        nextGeneratedId(coop.getMemberIdPrefix(), coop.getMemberIdType(), coop.getMemberIdPadding(), existingIds);
    Member outgoingAsMember =
        new Member(newMemberId, id, "member", oldPasswordHash, oldFirstName, oldLastName, oldEmail);
    outgoingAsMember.updateProfile(
        oldFirstName,
        oldLastName,
        oldOtherName,
        oldGender,
        oldPhone,
        oldEmail,
        oldNin,
        oldHomeAddress,
        oldCountry,
        oldState,
        oldCity,
        oldFacebook,
        oldTwitter,
        oldGuarantor,
        oldNextOfKinName,
        oldNextOfKinPhone,
        oldNextOfKinEmail,
        oldNextOfKinRelationship,
        oldNextOfKinAuthorityLevel,
        oldBankCode,
        oldAccountNumber,
        oldAccountName);
    memberRepository.save(outgoingAsMember);

    // The outgoing admin's own savings/loan history — if they had any while acting as admin —
    // follows them onto their new membership id; otherwise it would silently become the new
    // admin's history purely because it's keyed by an id that just changed hands.
    reassignRecords(id, outgoingAsMember.getId());
    if (promotedMember != null) {
      // Same reasoning in the other direction: the promoted member's own history follows them
      // onto the admin identity they now log in as.
      reassignRecords(promotedMemberOldId, id);
    }

    coop.updateDetails(
        coop.getName(),
        request.newFirstName() + " " + request.newLastName(),
        request.newEmail(),
        request.newPhone(),
        coop.getAddress(),
        coop.getCountry(),
        coop.getState(),
        coop.getCity());
    cooperativeRepository.save(coop);

    try {
      emailService.sendAdminWelcomeEmail(
          request.newEmail(),
          request.newFirstName() + " " + request.newLastName(),
          coop.getName(),
          coop.getId(),
          DEFAULT_ADMIN_PASSWORD);
    } catch (EmailDeliveryException e) {
      log.warn(
          "Admin transferred for {} but welcome email to {} failed: {}", coop.getId(), request.newEmail(), e.getMessage());
    }
    try {
      emailService.sendMemberWelcomeEmail(
          outgoingAsMember.getEmail(),
          outgoingAsMember.getFullName(),
          coop.getName(),
          outgoingAsMember.getId(),
          DEFAULT_ADMIN_PASSWORD);
    } catch (EmailDeliveryException e) {
      log.warn(
          "Outgoing admin for {} moved to member {} but notice email failed: {}",
          coop.getId(),
          outgoingAsMember.getId(),
          e.getMessage());
    }

    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Co-operatives",
        "Update",
        coop.getName() + " (admin transferred to " + request.newEmail() + ")",
        "Warning",
        httpRequest);

    notificationService.notify(
        outgoingAsMember.getId(),
        "MEMBER_ADDED",
        "You're now a member of " + coop.getName(),
        "You've moved from co-operative admin to a regular member, with a new membership ID: "
            + outgoingAsMember.getId() + ". Your password stays the same.",
        "/profile");

    notificationService.notifyAllSuperAdmins(
        "COOPERATIVE_ADMIN_TRANSFERRED",
        coop.getName() + " handed over its admin role",
        oldFirstName + " " + oldLastName + " handed the admin role over to " + request.newFirstName()
            + " " + request.newLastName() + " (" + request.newEmail() + ").",
        "/co-operatives/" + coop.getId());

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
        coop.getWithdrawalFeeAmount(),
        coop.getWithdrawalFeeType(),
        coop.getBankCode(),
        coop.getAccountNumber(),
        coop.getAccountName(),
        coop.getLogoUrl(),
        coop.getMemberIdPrefix(),
        coop.getMemberIdPadding(),
        coop.getMemberIdType(),
        coop.getMinGuarantors(),
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

  /** Moves every savings/loan record from one member id to another — used only by transferAdmin,
   * where a real person's own id changes but their financial history shouldn't stay behind
   * attached to whoever (or whatever role) now holds the id they used to have. */
  private void reassignRecords(String oldMemberId, String newMemberId) {
    savingsRecordRepository.reassignMember(oldMemberId, newMemberId);
    loanRecordRepository.reassignMember(oldMemberId, newMemberId);
  }

  /** Retires a member row being merged into the admin identity during transferAdmin — kept, not
   * deleted, since notifications/audit log/notices already reference it by id and deleting it
   * would either violate those foreign keys or destroy real history. Its email is swapped for a
   * synthetic, permanently-unique placeholder (freeing the real one up for the admin row it's
   * merging into) and its status set Inactive so it can never be logged into again — the person's
   * one real, active login is the admin identity from here on. */
  private void retireMember(Member member) {
    member.updateProfile(
        member.getFirstName(),
        member.getLastName(),
        member.getOtherName(),
        member.getGender(),
        member.getPhone(),
        member.getId().toLowerCase() + ".retired@merged.t-coop.internal",
        member.getNin(),
        member.getHomeAddress(),
        member.getCountry(),
        member.getState(),
        member.getCity(),
        member.getFacebook(),
        member.getTwitter(),
        member.getGuarantor(),
        member.getNextOfKinName(),
        member.getNextOfKinPhone(),
        member.getNextOfKinEmail(),
        member.getNextOfKinRelationship(),
        member.getNextOfKinAuthorityLevel(),
        member.getBankCode(),
        member.getAccountNumber(),
        member.getAccountName());
    member.setStatus("Inactive");
  }

  /** Super admin can access any co-op's members; an admin only their own — never trusts the
   * path's {id} for an admin caller, always checks it against their own cooperativeId. A member
   * with a coopRoleId (assigned a role via CoopUserController) gets the same co-op-scoped access
   * as the admin, for their own co-op only — the frontend's nav restricts which of these pages
   * they actually use day to day, but the backend grant itself isn't split per-permission, same
   * trust boundary "support" platform staff already have relative to super_admin endpoints. */
  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    boolean isCoopStaff = "admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    if (isCoopStaff && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You can only manage your own co-operative's members")));
  }
}
