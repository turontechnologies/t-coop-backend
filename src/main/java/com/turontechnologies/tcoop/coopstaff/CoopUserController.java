package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin's own Settings -> User Management -> Users, scoped to their own co-op (a super admin can
 * manage any co-op's too). Unlike platform staff, this does NOT create a new account — an admin
 * first creates the person as a regular member (Members Directory), then assigns them a CoopRole
 * here to elevate their access. They keep logging in with their existing membership ID and
 * password; assigning a role just adds coopRoleId, which AuthController resolves into
 * permissionModules on their next login. Removing them from staff only clears coopRoleId — their
 * underlying membership (savings, loans, etc.) is never touched.
 */
@RestController
public class CoopUserController {

  private final MemberRepository memberRepository;
  private final CoopRoleRepository coopRoleRepository;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;

  public CoopUserController(
      MemberRepository memberRepository,
      CoopRoleRepository coopRoleRepository,
      AuditLogService auditLogService,
      NotificationService notificationService) {
    this.memberRepository = memberRepository;
    this.coopRoleRepository = coopRoleRepository;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/users")
  public ResponseEntity<?> list(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Map<UUID, String> roleNames = new HashMap<>();
    for (CoopRole role : coopRoleRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id)) {
      roleNames.put(role.getId(), role.getName());
    }

    List<CoopUserDto> dtos =
        memberRepository.findAllByCooperativeIdAndCoopRoleIdIsNotNullOrderByFirstNameAsc(id).stream()
            .map(member -> CoopUserDto.from(member, roleNames.getOrDefault(member.getCoopRoleId(), "Unknown role")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** Assigns (or re-assigns) an existing member of this co-op to a role. The member must already
   * exist — see Members Directory to create one first — and can't be the co-op's own admin row
   * (id == cooperativeId), who already has full access. */
  @PatchMapping("/api/v1/cooperatives/{id}/users/{memberId}/role")
  public ResponseEntity<?> assignRole(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String memberId,
      @Valid @RequestBody UpdateCoopUserRoleRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    if (memberId.equals(id)) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "This member is already this co-operative's admin"));
    }
    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "We couldn't find that member in this co-operative"));
    }
    CoopRole role = findRole(request.roleId(), id);
    if (role == null || !"Active".equals(role.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }

    boolean wasAlreadyStaff = member.getCoopRoleId() != null;
    member.setCoopRoleId(role.getId());
    memberRepository.save(member);

    notificationService.notify(
        member.getId(),
        "COOP_ROLE_ASSIGNED",
        wasAlreadyStaff ? "Your role was updated" : "You were given a new role",
        "You now have \"" + role.getName() + "\" access. Log in again to see it reflected.",
        "/dashboard");

    logUpdate(authentication, wasAlreadyStaff ? "Update" : "Create", member.getFullName() + " -> " + role.getName(), httpRequest);
    return ResponseEntity.ok(CoopUserDto.from(member, role.getName()));
  }

  /** Revokes staff access — clears coopRoleId only. The member's account, membership, and every
   * other record they own stay exactly as they were; they simply lose the elevated permissions
   * and go back to being a regular member. */
  @DeleteMapping("/api/v1/cooperatives/{id}/users/{memberId}")
  public ResponseEntity<?> revoke(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String memberId,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || member.getCoopRoleId() == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that user"));
    }

    member.setCoopRoleId(null);
    memberRepository.save(member);

    notificationService.notify(
        member.getId(),
        "COOP_ROLE_REMOVED",
        "Your role was removed",
        "You no longer have staff access. You're still a member of this co-operative.",
        "/dashboard");

    logUpdate(authentication, "Delete", member.getFullName(), httpRequest);
    return ResponseEntity.ok(Map.of("message", "Role removed"));
  }

  private CoopRole findRole(String id, String cooperativeId) {
    try {
      CoopRole role = coopRoleRepository.findById(UUID.fromString(id)).orElse(null);
      return role != null && cooperativeId.equals(role.getCooperativeId()) ? role : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void logUpdate(
      Authentication authentication, String action, String resource, HttpServletRequest httpRequest) {
    String callerId = (String) authentication.getPrincipal();
    memberRepository
        .findById(callerId)
        .ifPresent(
            caller ->
                auditLogService.log(caller.getId(), caller.getRole(), "Users", action, resource, "Success", httpRequest));
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  /** Deliberately NOT widened to a coopRoleId holder, unlike Savings/Loans/Cooperative's
   * requireCoopAccess — this controller decides who gets what access in the first place (assign/
   * revoke a role), so letting an assigned staff member reach it too would let them grant
   * themselves (or anyone else) more permissions than they were given. Stays admin/super_admin. */
  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    if ("admin".equals(caller.getRole()) && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You can only manage your own co-operative's users")));
  }
}
