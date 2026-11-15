package com.turontechnologies.tcoop.audit;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super admin only: the whole audit log, a page at a time, with search and filters, and the same
 * filtered log as a CSV download. Nothing is capped: every entry is reachable by paging.
 */
@RestController
public class AuditLogController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int EXPORT_BATCH = 1000;

  private static final Map<String, String> ROLE_LABELS =
      Map.of(
          "super_admin", "Super Administrator",
          "admin", "Administrator",
          "member", "Member",
          "support", "Platform Staff");

  public record AuditLogPage(
      List<AuditLogEntryDto> content,
      int page,
      int size,
      long totalElements,
      int totalPages,
      Stats stats,
      List<String> modules) {}

  public record Stats(long total, long today, long failedToday) {}

  private record Filters(
      LocalDateTime from, LocalDateTime to, String module, String status, String role, String coopId, String q) {}

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final AuditLogRepository auditLogRepository;
  private final LocationResolver locationResolver;

  public AuditLogController(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      AuditLogRepository auditLogRepository,
      LocationResolver locationResolver) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.auditLogRepository = auditLogRepository;
    this.locationResolver = locationResolver;
  }

  @GetMapping("/api/v1/audit-log")
  public ResponseEntity<?> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String cooperativeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    ResponseEntity<?> denied = requireSuperAdmin(authentication);
    if (denied != null) return denied;

    Filters filters = filters(q, module, status, role, cooperativeId, from, to);
    int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    Page<AuditLog> result = search(filters, Math.max(page, 0), pageSize);

    LocalDateTime startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    Stats stats =
        new Stats(
            auditLogRepository.count(),
            auditLogRepository.countByCreatedAtGreaterThanEqual(startOfToday),
            auditLogRepository.countByCreatedAtGreaterThanEqualAndStatus(startOfToday, "Failed"));

    return ResponseEntity.ok(
        new AuditLogPage(
            toDtos(result.getContent()),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            stats,
            auditLogRepository.distinctModules()));
  }

  /** The filtered log as CSV — every matching entry, not just one page. */
  @GetMapping("/api/v1/audit-log/export")
  public void export(
      Authentication authentication,
      HttpServletResponse response,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String cooperativeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
      throws IOException {
    ResponseEntity<?> denied = requireSuperAdmin(authentication);
    if (denied != null) {
      response.setStatus(denied.getStatusCode().value());
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"Only a super admin can export the audit log\"}");
      return;
    }

    Filters filters = filters(q, module, status, role, cooperativeId, from, to);
    String fileName = "t-coop-activity-log-" + LocalDate.now() + ".csv";
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

    PrintWriter out = response.getWriter();
    out.write('﻿'); // so Excel opens it as UTF-8
    out.println(
        csv("Date (UTC)", "User", "User ID", "Role", "Co-operative", "Module", "Action", "Resource", "Status", "Location", "IP address"));
    int pageNumber = 0;
    Page<AuditLog> batch;
    do {
      batch = search(filters, pageNumber++, EXPORT_BATCH);
      Map<String, Member> actors = actorsOf(batch.getContent());
      Map<String, String> coopNames = coopNamesOf(actors);
      for (AuditLog entry : batch.getContent()) {
        AuditLogEntryDto dto = toDto(entry, actors, coopNames);
        out.println(
            csv(
                entry.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace('T', ' '),
                dto.activityBy(),
                entry.getActorId(),
                dto.role(),
                dto.cooperative(),
                dto.module(),
                dto.action(),
                dto.resource(),
                dto.status(),
                dto.location(),
                dto.ipAddress()));
      }
      out.flush();
    } while (batch.hasNext());
  }

  private Page<AuditLog> search(Filters f, int page, int size) {
    return auditLogRepository.search(
        f.from(), f.to(), f.module(), f.status(), f.role(), f.coopId(), f.q(), PageRequest.of(page, size));
  }

  private static Filters filters(
      String q, String module, String status, String role, String coopId, LocalDate from, LocalDate to) {
    String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    return new Filters(
        from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay(),
        to == null ? LocalDateTime.now(ZoneOffset.UTC).plusDays(1) : to.plusDays(1).atStartOfDay(),
        blank(module),
        blank(status),
        blank(role),
        blank(coopId),
        term.isEmpty() ? "" : "%" + term.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%");
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403).body(Map.of("error", "Only a super admin can view the audit log"));
    }
    return null;
  }

  private Map<String, Member> actorsOf(List<AuditLog> entries) {
    return memberRepository
        .findAllById(entries.stream().map(AuditLog::getActorId).filter(id -> id != null).distinct().toList())
        .stream()
        .collect(Collectors.toMap(Member::getId, member -> member));
  }

  private Map<String, String> coopNamesOf(Map<String, Member> actors) {
    List<String> ids =
        actors.values().stream().map(Member::getCooperativeId).filter(id -> id != null).distinct().toList();
    Map<String, String> names = new HashMap<>();
    for (Cooperative coop : cooperativeRepository.findAllById(ids)) names.put(coop.getId(), coop.getName());
    return names;
  }

  private List<AuditLogEntryDto> toDtos(List<AuditLog> entries) {
    Map<String, Member> actors = actorsOf(entries);
    Map<String, String> coopNames = coopNamesOf(actors);
    return entries.stream().map(entry -> toDto(entry, actors, coopNames)).toList();
  }

  private AuditLogEntryDto toDto(AuditLog entry, Map<String, Member> actors, Map<String, String> coopNames) {
    Member actor = entry.getActorId() == null ? null : actors.get(entry.getActorId());
    String activityBy = actor != null ? actor.getFullName() : (entry.getActorId() == null ? "System" : entry.getActorId());
    String roleLabel = ROLE_LABELS.getOrDefault(entry.getActorRole(), entry.getActorRole());
    String coop = actor == null || actor.getCooperativeId() == null ? null : coopNames.get(actor.getCooperativeId());

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
        entry.getIpAddress() == null ? "Unknown" : entry.getIpAddress(),
        coop);
  }

  /** One CSV line; values that a spreadsheet would run as a formula are made plain text. */
  private static String csv(String... values) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) line.append(',');
      String value = values[i] == null ? "" : values[i];
      if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) value = "'" + value;
      line.append('"').append(value.replace("\"", "\"\"")).append('"');
    }
    return line.toString();
  }
}
