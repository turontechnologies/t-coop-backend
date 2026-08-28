package com.turontechnologies.tcoop.member;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public (unauthenticated, {@code /api/v1/guarantor-invites/**} is permitAll — see
 * SecurityConfig) side of the guarantor accept-workflow — a guarantor never has their own
 * T-Coop account, so this is the only place they interact with the platform. Mirrors
 * PlatformInviteAcceptController's shape: look up by opaque token, validate it, act once.
 */
@RestController
public class GuarantorInviteController {

  private final MemberGuarantorRepository memberGuarantorRepository;
  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;

  public GuarantorInviteController(
      MemberGuarantorRepository memberGuarantorRepository,
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository) {
    this.memberGuarantorRepository = memberGuarantorRepository;
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
  }

  @GetMapping("/api/v1/guarantor-invites/{token}")
  public ResponseEntity<?> preview(@PathVariable String token) {
    MemberGuarantor guarantor = findByToken(token);
    if (guarantor == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "This invite link is invalid or has expired."));
    }
    String memberName =
        memberRepository.findById(guarantor.getMemberId()).map(Member::getFullName).orElse("a member");
    String cooperativeName =
        cooperativeRepository.findById(guarantor.getCooperativeId()).map(Cooperative::getName).orElse("their co-operative");
    return ResponseEntity.ok(
        new GuarantorInviteInfoDto(guarantor.getName(), memberName, cooperativeName, guarantor.getStatus()));
  }

  @PostMapping("/api/v1/guarantor-invites/accept")
  public ResponseEntity<?> accept(@Valid @RequestBody GuarantorInviteActionRequest request) {
    return respond(request.token(), "Accepted");
  }

  @PostMapping("/api/v1/guarantor-invites/decline")
  public ResponseEntity<?> decline(@Valid @RequestBody GuarantorInviteActionRequest request) {
    return respond(request.token(), "Declined");
  }

  private ResponseEntity<?> respond(String token, String status) {
    MemberGuarantor guarantor = findByToken(token);
    if (guarantor == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "This invite link is invalid or has expired."));
    }
    if (!"Pending".equals(guarantor.getStatus())) {
      return ResponseEntity.ok(
          Map.of("status", guarantor.getStatus(), "message", "You already responded to this request."));
    }
    guarantor.respond(status);
    memberGuarantorRepository.save(guarantor);
    return ResponseEntity.ok(Map.of("status", guarantor.getStatus()));
  }

  private MemberGuarantor findByToken(String token) {
    if (token == null || token.isBlank()) return null;
    MemberGuarantor guarantor = memberGuarantorRepository.findByAcceptToken(token).orElse(null);
    if (guarantor == null) return null;
    if (!"Pending".equals(guarantor.getStatus())) return guarantor;
    LocalDateTime expiresAt = guarantor.getAcceptTokenExpiresAt();
    if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) return null;
    return guarantor;
  }
}
