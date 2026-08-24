package com.turontechnologies.tcoop.platformstaff;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.auth.JwtService;
import com.turontechnologies.tcoop.auth.LoginResponse;
import com.turontechnologies.tcoop.auth.MemberDto;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public (unauthenticated, {@code /api/v1/platform-invites/**} is permitAll — see
 * SecurityConfig) side of the platform-staff invite flow — nobody here has a JWT yet. Mirrors
 * PasswordResetController's shape: look up by opaque token, validate expiry, act once. Platform
 * staff only — a co-op's own staff are assigned a CoopRole directly (see CoopUserController),
 * never invited through here, since they already have an active account.
 */
@RestController
public class PlatformInviteAcceptController {

  private final MemberRepository memberRepository;
  private final PlatformRoleRepository platformRoleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;

  public PlatformInviteAcceptController(
      MemberRepository memberRepository,
      PlatformRoleRepository platformRoleRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuditLogService auditLogService,
      NotificationService notificationService) {
    this.memberRepository = memberRepository;
    this.platformRoleRepository = platformRoleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
  }

  @GetMapping("/api/v1/platform-invites/{token}")
  public ResponseEntity<?> preview(@PathVariable String token) {
    Member member = validInvite(token);
    if (member == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "This invite link is invalid or has expired."));
    }
    String roleName =
        member.getPlatformRoleId() == null
            ? "Unknown role"
            : platformRoleRepository.findById(member.getPlatformRoleId())
                .map(PlatformRole::getName)
                .orElse("Unknown role");
    return ResponseEntity.ok(new InviteInfoDto(member.getEmail(), roleName));
  }

  @PostMapping("/api/v1/platform-invites/accept")
  public ResponseEntity<?> accept(
      @Valid @RequestBody AcceptInviteRequest request, HttpServletRequest httpRequest) {
    Member member = validInvite(request.token());
    if (member == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "This invite link is invalid or has expired."));
    }

    member.acceptInvite(
        request.firstName(), request.lastName(), passwordEncoder.encode(request.password()));
    memberRepository.save(member);

    auditLogService.log(
        member.getId(), member.getRole(), "Authentication", "Login", member.getEmail(), "Success", httpRequest);

    notificationService.notifyAllSuperAdmins(
        "PLATFORM_STAFF_JOINED",
        "New platform staff member",
        member.getFullName() + " (" + member.getEmail() + ") accepted their invite and can now log in.",
        "/settings");

    List<String> permissionModules =
        platformRoleRepository.findById(member.getPlatformRoleId())
            .map(PlatformRole::getPermissions)
            .orElse(List.of());

    // Log them straight in — the whole point of accepting is to land in the dashboard, not to
    // send them back to a login screen they don't have a "membership ID" habit for yet.
    String jwt = jwtService.generateToken(member);
    return ResponseEntity.ok(new LoginResponse(jwt, MemberDto.from(member, null, permissionModules)));
  }

  private Member validInvite(String token) {
    if (token == null || token.isBlank()) return null;
    Member member = memberRepository.findByInviteToken(token).orElse(null);
    if (member == null || !"Invited".equals(member.getStatus())) return null;
    LocalDateTime expiresAt = member.getInviteTokenExpiresAt();
    if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) return null;
    return member;
  }
}
