package com.turontechnologies.tcoop.platformstaff;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings -> User Management -> Users, super admin only — real platform-staff accounts, invited
 * by email, real login only after they accept (see {@link PlatformInviteAcceptController} for
 * the public accept-invite side). No seed data; every account here traces back to a real invite
 * a super admin sent. See documentation/flows.md for the full lifecycle.
 */
@RestController
public class PlatformUserController {

  private static final Logger log = LoggerFactory.getLogger(PlatformUserController.class);
  private static final int INVITE_VALID_DAYS = 7;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final MemberRepository memberRepository;
  private final PlatformRoleRepository platformRoleRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final AuditLogService auditLogService;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  public PlatformUserController(
      MemberRepository memberRepository,
      PlatformRoleRepository platformRoleRepository,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.platformRoleRepository = platformRoleRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/platform-users")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Map<UUID, String> roleNames = new HashMap<>();
    for (PlatformRole role : platformRoleRepository.findAll()) {
      roleNames.put(role.getId(), role.getName());
    }

    List<PlatformUserDto> dtos =
        memberRepository.findAllByRoleOrderByInvitedAtDesc("support").stream()
            .map(
                member ->
                    PlatformUserDto.from(
                        member, roleNames.getOrDefault(member.getPlatformRoleId(), "Unknown role")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/api/v1/platform-users/invite")
  public ResponseEntity<?> invite(
      Authentication authentication,
      @Valid @RequestBody InviteUserRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformRole role = findRole(request.roleId());
    if (role == null || !"Active".equals(role.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    if (memberRepository.findByEmail(request.email()).isPresent()) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That email address is already in use by another account"));
    }

    String id = generateStaffId();
    // Random, unguessable placeholder — makes login mathematically impossible until the invitee
    // sets their own password via accept-invite, without needing password_hash to be nullable.
    String placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());
    Member member = Member.invitePlatformStaff(id, request.email(), role.getId(), placeholderHash);
    String token = generateInviteToken();
    member.setInviteToken(
        token, LocalDateTime.now(ZoneOffset.UTC).plusDays(INVITE_VALID_DAYS));
    memberRepository.save(member);

    try {
      emailService.sendPlatformStaffInviteEmail(
          request.email(), role.getName(), frontendUrl + "/accept-invite?token=" + token);
    } catch (EmailDeliveryException e) {
      log.warn("Platform staff invite {} created but email to {} failed: {}", id, request.email(), e.getMessage());
    }

    logSettingsUpdate(authentication, "Create", request.email() + " (" + role.getName() + ")", httpRequest);
    return ResponseEntity.ok(PlatformUserDto.from(member, role.getName()));
  }

  @PatchMapping("/api/v1/platform-users/{id}/role")
  public ResponseEntity<?> updateRole(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody UserRoleUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Member member = findStaffMember(id);
    if (member == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that user"));
    }
    PlatformRole role = findRole(request.roleId());
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }

    member.setPlatformRoleId(role.getId());
    memberRepository.save(member);

    logSettingsUpdate(authentication, "Update", member.getEmail() + " → " + role.getName(), httpRequest);
    return ResponseEntity.ok(PlatformUserDto.from(member, role.getName()));
  }

  @PatchMapping("/api/v1/platform-users/{id}/status")
  public ResponseEntity<?> updateStatus(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody UserStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Member member = findStaffMember(id);
    if (member == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that user"));
    }
    if ("Invited".equals(member.getStatus()) && "Active".equals(request.status())) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "This user hasn't accepted their invite yet."));
    }

    member.setStatus(request.status());
    memberRepository.save(member);

    String roleName = resolveRoleName(member);
    logSettingsUpdate(
        authentication,
        "Update",
        member.getEmail(),
        httpRequest,
        "Inactive".equals(request.status()) ? "Warning" : "Success");
    return ResponseEntity.ok(PlatformUserDto.from(member, roleName));
  }

  @PostMapping("/api/v1/platform-users/{id}/resend-invite")
  public ResponseEntity<?> resendInvite(
      Authentication authentication, @PathVariable String id, HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Member member = findStaffMember(id);
    if (member == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that user"));
    }
    if (!"Invited".equals(member.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This user already accepted their invite."));
    }

    String token = generateInviteToken();
    member.setInviteToken(token, LocalDateTime.now(ZoneOffset.UTC).plusDays(INVITE_VALID_DAYS));
    memberRepository.save(member);

    String roleName = resolveRoleName(member);
    try {
      emailService.sendPlatformStaffInviteEmail(
          member.getEmail(), roleName, frontendUrl + "/accept-invite?token=" + token);
    } catch (EmailDeliveryException e) {
      return ResponseEntity.status(502).body(Map.of("error", "Couldn't send the invite email. Please try again."));
    }

    logSettingsUpdate(authentication, "Update", member.getEmail() + " (invite resent)", httpRequest);
    return ResponseEntity.ok(Map.of("message", "Invite resent"));
  }

  @DeleteMapping("/api/v1/platform-users/{id}")
  public ResponseEntity<?> delete(
      Authentication authentication, @PathVariable String id, HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Member member = findStaffMember(id);
    if (member == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that user"));
    }

    memberRepository.delete(member);
    logSettingsUpdate(authentication, "Delete", member.getEmail(), httpRequest, "Warning");
    return ResponseEntity.ok(Map.of("message", "User removed"));
  }

  private String resolveRoleName(Member member) {
    if (member.getPlatformRoleId() == null) return "Unknown role";
    return platformRoleRepository.findById(member.getPlatformRoleId()).map(PlatformRole::getName).orElse("Unknown role");
  }

  private Member findStaffMember(String id) {
    Member member = memberRepository.findById(id).orElse(null);
    return member != null && "support".equals(member.getRole()) ? member : null;
  }

  private PlatformRole findRole(String id) {
    try {
      return platformRoleRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String generateStaffId() {
    long n = memberRepository.findAllByRoleOrderByInvitedAtDesc("support").size() + 1;
    return "SUP-" + String.format("%04d", n);
  }

  private String generateInviteToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private void logSettingsUpdate(
      Authentication authentication, String action, String resource, HttpServletRequest httpRequest) {
    logSettingsUpdate(authentication, action, resource, httpRequest, "Success");
  }

  private void logSettingsUpdate(
      Authentication authentication,
      String action,
      String resource,
      HttpServletRequest httpRequest,
      String status) {
    String callerId = (String) authentication.getPrincipal();
    memberRepository
        .findById(callerId)
        .ifPresent(
            caller ->
                auditLogService.log(
                    caller.getId(), caller.getRole(), "Users", action, resource, status, httpRequest));
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage platform users"));
    }
    return null;
  }
}
