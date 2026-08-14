package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
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

  public AuthController(
      MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    var member = memberRepository.findById(request.membershipId()).orElse(null);

    if (member == null
        || !"Active".equals(member.getStatus())
        || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
      return ResponseEntity.status(401).body(Map.of("error", INVALID_CREDENTIALS));
    }

    String token = jwtService.generateToken(member);
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
}
