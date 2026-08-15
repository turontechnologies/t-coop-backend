package com.turontechnologies.tcoop.audit;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditLogController {

  private static final int MAX_ENTRIES = 200;

  private static final Map<String, String> ROLE_LABELS =
      Map.of(
          "super_admin", "Super Administrator",
          "admin", "Administrator",
          "member", "Member");

  private final MemberRepository memberRepository;
  private final AuditLogRepository auditLogRepository;
  private final LocationResolver locationResolver;

  public AuditLogController(
      MemberRepository memberRepository,
      AuditLogRepository auditLogRepository,
      LocationResolver locationResolver) {
    this.memberRepository = memberRepository;
    this.auditLogRepository = auditLogRepository;
    this.locationResolver = locationResolver;
  }

  @GetMapping("/api/v1/audit-log")
  public ResponseEntity<?> list(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can view the audit log"));
    }

    List<AuditLog> entries =
        auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_ENTRIES));

    Map<String, Member> actorsById =
        memberRepository
            .findAllById(entries.stream().map(AuditLog::getActorId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member));

    List<AuditLogEntryDto> dtos = entries.stream().map(entry -> toDto(entry, actorsById)).toList();
    return ResponseEntity.ok(dtos);
  }

  private AuditLogEntryDto toDto(AuditLog entry, Map<String, Member> actorsById) {
    Member actor = actorsById.get(entry.getActorId());
    String activityBy = actor != null ? actor.getFullName() : entry.getActorId();
    String roleLabel = ROLE_LABELS.getOrDefault(entry.getActorRole(), entry.getActorRole());

    return new AuditLogEntryDto(
        "log-" + entry.getId(),
        entry.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
        activityBy,
        roleLabel,
        entry.getModule(),
        entry.getAction(),
        entry.getResource(),
        entry.getStatus(),
        locationResolver.resolve(entry.getIpAddress()),
        entry.getIpAddress() == null ? "Unknown" : entry.getIpAddress());
  }
}
