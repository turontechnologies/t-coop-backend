package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The password new members of a co-op start with. The co-op's admin (or a super admin) chooses
 * it; it's put in each new member's welcome email. Members already added keep their password.
 */
@RestController
public class CoopDefaultPasswordController {

  /** Used when a co-op hasn't chosen its own — must match CooperativeController's default. */
  public static final String PLATFORM_DEFAULT = "admin123";

  /** The password a new member of this co-op starts with: the admin's choice, or the platform's. */
  public static String startingPasswordFor(Cooperative coop) {
    String own = coop.getDefaultMemberPassword();
    return own != null && !own.isBlank() ? own : PLATFORM_DEFAULT;
  }

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;

  public CoopDefaultPasswordController(
      CooperativeRepository cooperativeRepository, MemberRepository memberRepository, AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
  }

  public record DefaultPasswordDto(String password, boolean ownPassword) {}

  public record DefaultPasswordRequest(@Size(max = 100, message = "Keep it under 100 characters") String password) {}

  @GetMapping("/api/v1/cooperatives/{id}/default-password")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    ResponseEntity<?> denied = requireAdmin(authentication, id);
    if (denied != null) return denied;
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    String own = coop.getDefaultMemberPassword();
    return ResponseEntity.ok(new DefaultPasswordDto(own != null ? own : PLATFORM_DEFAULT, own != null));
  }

  /** An empty password goes back to the platform's default. */
  @PutMapping("/api/v1/cooperatives/{id}/default-password")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody DefaultPasswordRequest request,
      HttpServletRequest httpRequest) {
    ResponseEntity<?> denied = requireAdmin(authentication, id);
    if (denied != null) return denied;
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));

    String password = request.password() == null ? "" : request.password();
    if (!password.isEmpty()) {
      if (!password.equals(password.trim()) || password.contains(" ")) {
        return ResponseEntity.badRequest().body(Map.of("error", "The password can't have spaces in it"));
      }
      if (password.length() < 6) {
        return ResponseEntity.badRequest().body(Map.of("error", "The password must have at least 6 characters"));
      }
      if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
        return ResponseEntity.badRequest().body(Map.of("error", "Use at least one letter and one number"));
      }
    }
    coop.setDefaultMemberPassword(password);
    cooperativeRepository.save(coop);

    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller != null) {
      auditLogService.log(
          caller.getId(), caller.getRole(), "Settings", "Update", "Default member password", "Success", httpRequest);
    }
    String own = coop.getDefaultMemberPassword();
    return ResponseEntity.ok(new DefaultPasswordDto(own != null ? own : PLATFORM_DEFAULT, own != null));
  }

  /** The co-op's own admin, or a super admin. */
  private ResponseEntity<?> requireAdmin(Authentication authentication, String cooperativeId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if ("super_admin".equals(caller.getRole())) return null;
    boolean isCoopAdmin =
        caller.getId().equals(cooperativeId)
            || ("admin".equals(caller.getRole()) && cooperativeId.equals(caller.getCooperativeId()));
    if (isCoopAdmin) return null;
    return ResponseEntity.status(403).body(Map.of("error", "Only the co-operative's admin can do this"));
  }
}
