package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The forgot-password recovery flow — separate from ProfileController's authenticated
 * change-password endpoint, since nobody here has a valid JWT yet (that's the whole point).
 * Three steps: request an OTP by email, verify it for a short-lived reset token, then use that
 * token to actually set a new password. See documentation/flows.md for the full sequence.
 */
@RestController
public class PasswordResetController {

  private static final int OTP_VALID_MINUTES = 10;
  private static final int RESET_TOKEN_VALID_MINUTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final MemberRepository memberRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final AuditLogService auditLogService;

  public PasswordResetController(
      MemberRepository memberRepository,
      PasswordResetTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/api/v1/auth/forgot-password")
  public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    Member member = memberRepository.findByEmail(request.email()).orElse(null);
    if (member == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "We couldn't find an account with that email address"));
    }

    String otp = generateOtp();
    PasswordResetToken token =
        new PasswordResetToken(
            request.email(),
            passwordEncoder.encode(otp),
            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(OTP_VALID_MINUTES));
    tokenRepository.save(token);

    // Throws EmailDeliveryException (-> 502) if this genuinely fails — the token row is
    // already saved, which is harmless: it just sits unused until it expires.
    emailService.sendOtpEmail(request.email(), member.getFullName(), otp);

    return ResponseEntity.ok(Map.of("message", "OTP sent"));
  }

  @PostMapping("/api/v1/auth/verify-otp")
  public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
    PasswordResetToken token =
        tokenRepository
            .findFirstByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                request.email(), LocalDateTime.now(ZoneOffset.UTC))
            .orElse(null);

    if (token == null) {
      return ResponseEntity.status(400)
          .body(Map.of("error", "That code has expired. Please request a new one."));
    }
    if (!passwordEncoder.matches(request.otp(), token.getOtpHash())) {
      return ResponseEntity.status(400).body(Map.of("error", "Incorrect OTP. Please try again."));
    }

    String resetToken = generateResetToken();
    token.markOtpVerified(resetToken);
    tokenRepository.save(token);

    return ResponseEntity.ok(Map.of("resetToken", resetToken));
  }

  @PostMapping("/api/v1/auth/reset-password")
  public ResponseEntity<?> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
    PasswordResetToken token =
        tokenRepository
            .findFirstByResetTokenAndUsedFalseAndExpiresAtAfter(
                request.resetToken(), LocalDateTime.now(ZoneOffset.UTC))
            .orElse(null);

    if (token == null) {
      return ResponseEntity.status(400)
          .body(Map.of("error", "This reset link has expired. Please request a new OTP."));
    }

    Member member = memberRepository.findByEmail(token.getEmail()).orElse(null);
    if (member == null) {
      return ResponseEntity.status(400).body(Map.of("error", "Account no longer exists"));
    }

    member.changePassword(passwordEncoder.encode(request.newPassword()));
    memberRepository.save(member);
    token.markUsed();
    tokenRepository.save(token);

    auditLogService.log(
        member.getId(),
        member.getRole(),
        "Authentication",
        "Update",
        "Password Reset",
        "Success",
        httpRequest);

    return ResponseEntity.ok(Map.of("message", "Password updated"));
  }

  private String generateOtp() {
    return String.valueOf(100000 + RANDOM.nextInt(900000));
  }

  private String generateResetToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
