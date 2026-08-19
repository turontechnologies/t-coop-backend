package com.turontechnologies.tcoop.platformstaff;

import com.turontechnologies.tcoop.audit.AuditLogService;
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
 * Settings -> User Management -> Roles, super admin only. Real backend for what used to be
 * `useSettingsStore`'s `platformRoles` — no seed data, a super admin creates every role
 * themselves. See documentation/flows.md for the full invite lifecycle these roles gate.
 */
@RestController
public class PlatformRoleController {

  private final PlatformRoleRepository platformRoleRepository;
  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;

  public PlatformRoleController(
      PlatformRoleRepository platformRoleRepository,
      MemberRepository memberRepository,
      AuditLogService auditLogService) {
    this.platformRoleRepository = platformRoleRepository;
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/platform-roles")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<PlatformRoleDto> dtos =
        platformRoleRepository.findAllByOrderByCreatedAtAsc().stream()
            .map(PlatformRoleDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/api/v1/platform-roles")
  public ResponseEntity<?> create(
      Authentication authentication,
      @Valid @RequestBody PlatformRoleCreateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    if (platformRoleRepository.existsByNameIgnoreCase(request.name())) {
      return ResponseEntity.status(409).body(Map.of("error", "That role name is already in use."));
    }

    PlatformRole role = new PlatformRole(request.name(), request.permissions());
    platformRoleRepository.save(role);

    logSettingsUpdate(authentication, "Create", role.getName(), httpRequest);
    return ResponseEntity.ok(PlatformRoleDto.from(role));
  }

  @PatchMapping("/api/v1/platform-roles/{id}")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody PlatformRoleCreateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformRole role = findRole(id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    boolean nameTakenByAnother =
        platformRoleRepository
            .findByNameIgnoreCase(request.name())
            .map(existing -> !existing.getId().equals(role.getId()))
            .orElse(false);
    if (nameTakenByAnother) {
      return ResponseEntity.status(409).body(Map.of("error", "That role name is already in use."));
    }

    role.update(request.name(), request.permissions());
    platformRoleRepository.save(role);

    logSettingsUpdate(authentication, "Update", role.getName(), httpRequest);
    return ResponseEntity.ok(PlatformRoleDto.from(role));
  }

  @PatchMapping("/api/v1/platform-roles/{id}/status")
  public ResponseEntity<?> updateStatus(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody PlatformRoleStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformRole role = findRole(id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    role.setStatus(request.status());
    platformRoleRepository.save(role);

    logSettingsUpdate(authentication, "Update", role.getName(), httpRequest);
    return ResponseEntity.ok(PlatformRoleDto.from(role));
  }

  @DeleteMapping("/api/v1/platform-roles/{id}")
  public ResponseEntity<?> delete(
      Authentication authentication, @PathVariable String id, HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    PlatformRole role = findRole(id);
    if (role == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that role"));
    }
    if (memberRepository.countByPlatformRoleId(role.getId()) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "This role is still assigned to a user — reassign them first."));
    }

    platformRoleRepository.delete(role);
    logSettingsUpdate(authentication, "Delete", role.getName(), httpRequest);
    return ResponseEntity.ok(Map.of("message", "Role removed"));
  }

  private PlatformRole findRole(String id) {
    try {
      return platformRoleRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void logSettingsUpdate(
      Authentication authentication, String action, String resource, HttpServletRequest httpRequest) {
    String callerId = (String) authentication.getPrincipal();
    memberRepository
        .findById(callerId)
        .ifPresent(
            caller ->
                auditLogService.log(
                    caller.getId(), caller.getRole(), "Users", action, resource, "Success", httpRequest));
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage platform roles"));
    }
    return null;
  }
}
