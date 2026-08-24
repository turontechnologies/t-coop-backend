package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin's own Settings -> User Management -> Roles, scoped to their own co-op (a super admin can
 * manage any co-op's roles too, same oversight pattern as everything else co-op-scoped). Real
 * backend, mirrors PlatformRoleController exactly — see that class and CoopRole for the full
 * "why" — no seed data, an admin creates every role themselves.
 */
@RestController
public class CoopRoleController {

  private final CoopRoleRepository coopRoleRepository;
  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;

  public CoopRoleController(
      CoopRoleRepository coopRoleRepository,
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      AuditLogService auditLogService) {
    this.coopRoleRepository = coopRoleRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/roles")
  public ResponseEntity<?> list(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    List<CoopRoleDto> dtos =
        coopRoleRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id).stream().map(CoopRoleDto::from).toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/api/v1/cooperatives/{id}/roles")
  public ResponseEntity<?> create(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CoopRoleCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    if (coopRoleRepository.existsByCooperativeIdAndNameIgnoreCase(id, request.name())) {
      return ResponseEntity.status(409).body(Map.of("error", "That role name is already in use."));
    }

    CoopRole role = new CoopRole(id, request.name(), request.permissions());
    coopRoleRepository.save(role);

    logUpdate(authentication, "Create", role.getName(), httpRequest);
    return ResponseEntity.ok(CoopRoleDto.from(role));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/roles/{roleId}")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String roleId,
      @Valid @RequestBody CoopRoleCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    CoopRole role = findRole(roleId, id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    boolean nameTakenByAnother =
        coopRoleRepository
            .findByCooperativeIdAndNameIgnoreCase(id, request.name())
            .map(existing -> !existing.getId().equals(role.getId()))
            .orElse(false);
    if (nameTakenByAnother) {
      return ResponseEntity.status(409).body(Map.of("error", "That role name is already in use."));
    }

    role.update(request.name(), request.permissions());
    coopRoleRepository.save(role);

    logUpdate(authentication, "Update", role.getName(), httpRequest);
    return ResponseEntity.ok(CoopRoleDto.from(role));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/roles/{roleId}/status")
  public ResponseEntity<?> updateStatus(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String roleId,
      @Valid @RequestBody CoopRoleStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    CoopRole role = findRole(roleId, id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    role.setStatus(request.status());
    coopRoleRepository.save(role);

    logUpdate(authentication, "Update", role.getName(), httpRequest);
    return ResponseEntity.ok(CoopRoleDto.from(role));
  }

  @DeleteMapping("/api/v1/cooperatives/{id}/roles/{roleId}")
  public ResponseEntity<?> delete(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String roleId,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    CoopRole role = findRole(roleId, id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    if (memberRepository.countByCoopRoleId(role.getId()) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "This role is still assigned to a user — reassign them first."));
    }

    coopRoleRepository.delete(role);
    logUpdate(authentication, "Delete", role.getName(), httpRequest);
    return ResponseEntity.ok(Map.of("message", "Role removed"));
  }

  private CoopRole findRole(String roleId, String cooperativeId) {
    try {
      CoopRole role = coopRoleRepository.findById(UUID.fromString(roleId)).orElse(null);
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
   * requireCoopAccess — this controller decides who gets what access in the first place, so
   * letting an assigned staff member reach it too would let them grant themselves (or anyone
   * else) more permissions than they were given. Stays admin/super_admin-only. */
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
            .body(Map.of("error", "You can only manage your own co-operative's roles")));
  }
}
