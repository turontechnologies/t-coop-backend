package com.turontechnologies.tcoop.profile;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;

  public ProfileController(
      MemberRepository memberRepository,
      AuditLogService auditLogService,
      PasswordEncoder passwordEncoder) {
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/api/v1/profile")
  public ResponseEntity<?> getProfile(Authentication authentication) {
    Member member = requireCaller(authentication);
    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    return ResponseEntity.ok(ProfileDto.from(member));
  }

  @PatchMapping("/api/v1/profile")
  public ResponseEntity<?> updateProfile(
      Authentication authentication,
      @Valid @RequestBody ProfileUpdateRequest request,
      HttpServletRequest httpRequest) {
    Member member = requireCaller(authentication);
    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    boolean emailTakenByAnotherMember =
        memberRepository
            .findByEmail(request.email())
            .filter(existing -> !existing.getId().equals(member.getId()))
            .isPresent();
    if (emailTakenByAnotherMember) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That email address is already in use by another account"));
    }

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
        request.guarantor(),
        request.nextOfKinName(),
        request.nextOfKinPhone(),
        request.nextOfKinEmail(),
        request.nextOfKinRelationship(),
        request.nextOfKinAuthorityLevel(),
        request.bankCode(),
        request.accountNumber(),
        request.accountName());
    memberRepository.save(member);

    // module/action/resource here must match the frontend's fixed
    // AuditModule/AuditAction enums (src/lib/audit-log-data.ts) exactly —
    // an unrecognized value has no icon mapping and breaks the Logs tab.
    auditLogService.log(
        member.getId(), member.getRole(), "Settings", "Update", "Profile", "Success", httpRequest);

    return ResponseEntity.ok(ProfileDto.from(member));
  }

  @PostMapping("/api/v1/profile/password")
  public ResponseEntity<?> changePassword(
      Authentication authentication,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest) {
    Member member = requireCaller(authentication);
    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
      return ResponseEntity.status(400).body(Map.of("error", "Current password is incorrect"));
    }

    member.changePassword(passwordEncoder.encode(request.newPassword()));
    memberRepository.save(member);

    auditLogService.log(
        member.getId(), member.getRole(), "Settings", "Update", "Password", "Success", httpRequest);

    return ResponseEntity.ok(Map.of("message", "Password updated"));
  }

  private Member requireCaller(Authentication authentication) {
    String memberId = (String) authentication.getPrincipal();
    return memberRepository.findById(memberId).orElse(null);
  }
}
