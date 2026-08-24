package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopRole;
import com.turontechnologies.tcoop.coopstaff.CoopRoleRepository;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.platformstaff.PlatformRole;
import com.turontechnologies.tcoop.platformstaff.PlatformRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private static final String INVALID_CREDENTIALS = "Invalid membership ID or password";
  private static final String ACCOUNT_NOT_ACTIVE =
      "Your account is not active. Please contact Turon Technologies for assistance.";

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final PlatformRoleRepository platformRoleRepository;
  private final CoopRoleRepository coopRoleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditLogService auditLogService;

  public AuthController(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      PlatformRoleRepository platformRoleRepository,
      CoopRoleRepository coopRoleRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.platformRoleRepository = platformRoleRepository;
    this.coopRoleRepository = coopRoleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.auditLogService = auditLogService;
  }

  private MemberDto toDto(Member member) {
    Cooperative coop =
        "admin".equals(member.getRole()) && member.getCooperativeId() != null
            ? cooperativeRepository.findById(member.getCooperativeId()).orElse(null)
            : null;
    List<String> permissionModules;
    if ("support".equals(member.getRole()) && member.getPlatformRoleId() != null) {
      permissionModules =
          platformRoleRepository
              .findById(member.getPlatformRoleId())
              .map(PlatformRole::getPermissions)
              .orElse(List.of());
    } else if (member.getCoopRoleId() != null) {
      permissionModules =
          coopRoleRepository.findById(member.getCoopRoleId()).map(CoopRole::getPermissions).orElse(List.of());
    } else {
      permissionModules = null;
    }
    return MemberDto.from(member, coop, permissionModules);
  }

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    // Flexible on purpose: a co-op/member's own id, or any account's email, both work here —
    // platform staff in particular only ever know their email, never a "membership ID".
    var member =
        memberRepository
            .findById(request.membershipId())
            .or(() -> memberRepository.findByEmail(request.membershipId()))
            .orElse(null);

    if (member == null || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
      return ResponseEntity.status(401).body(Map.of("error", INVALID_CREDENTIALS));
    }

    // Only reveal "account disabled" once the password has already proven who's asking —
    // otherwise an anonymous guesser could use this response to fish for disabled accounts.
    if (!"Active".equals(member.getStatus())) {
      return ResponseEntity.status(403).body(Map.of("error", ACCOUNT_NOT_ACTIVE));
    }

    String token = jwtService.generateToken(member);
    auditLogService.log(
        member.getId(),
        member.getRole(),
        "Authentication",
        "Login",
        member.getEmail(),
        "Success",
        httpRequest);
    return ResponseEntity.ok(new LoginResponse(token, toDto(member)));
  }

  @GetMapping("/api/v1/auth/me")
  public ResponseEntity<?> me(Authentication authentication) {
    // /api/v1/auth/** is permitAll() in SecurityConfig (so login/forgot-password work
    // unauthenticated), so this endpoint IS reachable with no token or a garbage one —
    // JwtAuthenticationFilter simply never populates `authentication` in that case.
    String memberId = resolveMemberId(authentication);
    if (memberId == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
    }
    var member = memberRepository.findById(memberId).orElse(null);

    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    return ResponseEntity.ok(toDto(member));
  }

  @PostMapping("/api/v1/auth/logout")
  public ResponseEntity<?> logout(Authentication authentication, HttpServletRequest httpRequest) {
    // JWTs are stateless — there's no server-side session to destroy. This
    // endpoint exists so (a) the frontend has one consistent call to make
    // regardless of auth strategy, (b) logout is audit-logged, and (c) it's
    // the natural place to add token blacklisting later if that's ever
    // needed. The frontend is responsible for discarding its copy of the
    // token either way.
    String memberId = resolveMemberId(authentication);
    if (memberId != null) {
      var member = memberRepository.findById(memberId).orElse(null);
      if (member != null) {
        auditLogService.log(
            member.getId(),
            member.getRole(),
            "Authentication",
            "Logout",
            member.getEmail(),
            "Success",
            httpRequest);
      }
    }
    return ResponseEntity.ok(Map.of("message", "Logged out"));
  }

  /** Returns the real member ID if a valid token was sent, null for an anonymous/no-token request. */
  private String resolveMemberId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) return null;
    Object principal = authentication.getPrincipal();
    return principal instanceof String id && !"anonymousUser".equals(id) ? id : null;
  }
}
