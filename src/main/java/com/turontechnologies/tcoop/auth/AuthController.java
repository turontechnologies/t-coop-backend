package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditLogService auditLogService;

  public AuthController(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    var member = memberRepository.findById(request.membershipId()).orElse(null);

    if (member == null
        || !"Active".equals(member.getStatus())
        || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
      return ResponseEntity.status(401).body(Map.of("error", INVALID_CREDENTIALS));
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
    return ResponseEntity.ok(new LoginResponse(token, MemberDto.from(member)));
  }

  @GetMapping("/api/v1/auth/me")
  public ResponseEntity<?> me(Authentication authentication) {
    // authentication is set by JwtAuthenticationFilter; SecurityConfig already
    // guarantees this endpoint isn't reached without a valid token.
    String memberId = (String) authentication.getPrincipal();
    var member = memberRepository.findById(memberId).orElse(null);

    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    return ResponseEntity.ok(MemberDto.from(member));
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
